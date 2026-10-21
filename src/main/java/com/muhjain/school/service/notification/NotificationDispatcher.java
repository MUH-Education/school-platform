package com.muhjain.school.service.notification;

import com.muhjain.school.exception.MediaPublishException;
import com.muhjain.school.model.dto.response.GuardOutcome;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.ResolvedAudience;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.MessageStatus;
import com.muhjain.school.model.enums.NotificationStatus;
import com.muhjain.school.model.enums.RecipientType;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.model.repository.MessageOutboxRepository;
import com.muhjain.school.model.repository.NotificationRepository;
import com.muhjain.school.util.Placeholders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Turns notifications into outbox rows (rule 2 of the phase file). Real-life picture: the clerk who makes 244 copies
 * of the office's letter, one per family. {@code NotificationDispatchScheduler} calls {@link #runOnce()} every
 * 60 seconds; a test calls it by hand.
 * <ol>
 * <li><b>Expand</b> every notification that is SCHEDULED and due, or SENDING with no rows yet ("send now", or a
 * crash in the middle). The list is made now (rule 3), every person passes the guard chain, and one row per person
 * and channel is written: QUEUED, or SKIPPED with its reason. One transaction per notification, claimed with
 * {@code for update skip locked}: two app instances never expand the same one, and a crash leaves all rows or
 * none.</li>
 * <li><b>Close</b> every SENDING notification with no QUEUED row left: SENT (FAILED when every row that was tried
 * failed), {@code finished_at}.</li>
 * </ol>
 * The bodies are never logged.
 */
@Component
public class NotificationDispatcher {

	private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

	/** At most this many notifications are expanded in one round; the rest wait one minute. */
	static final int MAX_PER_ROUND = 20;

	/** The rows that were tried and did not fail. */
	private static final Set<MessageStatus> WENT = EnumSet.of(MessageStatus.SENT, MessageStatus.DELIVERED,
			MessageStatus.TEST_ONLY);

	private final NotificationRepository notifications;

	private final MessageOutboxRepository outbox;

	private final AudienceService audienceService;

	private final GuardChain guardChain;

	private final KindPolicies policies;

	private final NotificationMediaService mediaService;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public NotificationDispatcher(NotificationRepository notifications, MessageOutboxRepository outbox,
			AudienceService audienceService, GuardChain guardChain, KindPolicies policies,
			NotificationMediaService mediaService, PlatformTransactionManager transactionManager, Clock clock) {
		this.notifications = notifications;
		this.outbox = outbox;
		this.audienceService = audienceService;
		this.guardChain = guardChain;
		this.policies = policies;
		this.mediaService = mediaService;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** One round. Returns how many notifications it expanded or closed. */
	public int runOnce() {
		int handled = 0;
		for (int i = 0; i < MAX_PER_ROUND; i++) {
			AtomicReference<Long> current = new AtomicReference<>();
			Boolean expanded;
			try {
				expanded = transaction.execute(status -> expandOne(current));
			}
			catch (MediaPublishException ex) {
				// The provider did not take the file: nothing was written, the notification stays as it is and is
				// tried again next minute (a provider that is down is down for every notification).
				log.warn("Notification {} waits: {}", current.get(), ex.getMessage());
				break;
			}
			catch (RuntimeException ex) {
				// Nothing of it was written (the transaction rolled back). It is marked FAILED, so it is not claimed
				// first again every minute, blocking every notification behind it.
				log.error("Notification {} could not be expanded", current.get(), ex);
				if (current.get() == null) {
					break;   // failed before claiming anything: try again next minute
				}
				markBroken(current.get());
				handled++;
				continue;
			}
			if (!Boolean.TRUE.equals(expanded)) {
				break;
			}
			handled++;
		}
		Integer closed = transaction.execute(status -> closeFinished());
		return handled + ((closed == null) ? 0 : closed);
	}

	// Claims one notification to expand and writes its rows. False when there is none.
	private boolean expandOne(AtomicReference<Long> current) {
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		Optional<Notification> claimed = notifications.claimDue(now, 1).stream().findFirst();
		if (claimed.isEmpty()) {
			claimed = notifications.findSendingWithoutExpansion(1).stream().findFirst();
		}
		if (claimed.isEmpty()) {
			return false;
		}
		current.set(claimed.get().getId());
		expand(claimed.get(), now);
		return true;
	}

	private void expand(Notification n, Instant now) {
		// The file first: the provider must have it before any message points at it.
		mediaService.ensurePublished(n);
		KindPolicy policy = policies.forKind(n.getKind());
		ResolvedAudience audience = audienceService.resolve(n.getKind(), n.getAudience());
		GuardContext context = guardChain.newContext(n);
		List<MessageOutbox> rows = new ArrayList<>();
		int people = 0;
		int skipped = 0;
		// The stopped families go through the chain too, so they are written as SKIPPED STOPPED for the report.
		for (Recipient r : Stream.concat(audience.recipients().stream(), audience.stopped().stream()).toList()) {
			GuardOutcome outcome = guardChain.check(r, n, context);
			if (!outcome.mayGo()) {
				// No phone, or a second copy to the same phone: there is no row to write (the phone column
				// needs a real number, and one phone has one row per notification).
				if (outcome.skipReason() != SkipReason.NO_PHONE && outcome.skipReason() != SkipReason.DUPLICATE) {
					MessageOutbox row = row(n, policy, r, MessageChannel.WHATSAPP, outcome, now);
					row.markSkipped(outcome.skipReason());
					rows.add(row);
				}
				skipped++;
				continue;
			}
			people++;
			rows.add(row(n, policy, r, MessageChannel.WHATSAPP, outcome, now));
			if (policy.alsoSmsWhenOnWhatsApp()) {
				rows.add(row(n, policy, r, MessageChannel.SMS, outcome, now));
			}
		}
		outbox.saveAll(rows);
		n.setStatus(NotificationStatus.SENDING);
		n.setExpandedAt(now);
		n.setRecipientCount(people);
		notifications.save(n);
		log.info("Notification {} ({}) expanded: {} people, {} skipped, {} rows", n.getId(), n.getKind(), people,
				skipped, rows.size());
	}

	// One row for one person and channel. The text is filled now: the person's values (the guards may have put the
	// live fee numbers in), then the values that are the same for everyone.
	private static MessageOutbox row(Notification n, KindPolicy policy, Recipient r, MessageChannel channel,
			GuardOutcome outcome, Instant now) {
		Map<String, String> values = new LinkedHashMap<>(outcome.values());
		values.putAll(n.getFixedValues());
		String text = (channel == MessageChannel.SMS && n.getSmsBody() != null) ? n.getSmsBody() : n.getBody();
		MessageOutbox row = new MessageOutbox(policy.outboxPurpose(), channel, r.phone(),
				cut(Placeholders.fill(text, values)), (outcome.holdUntil() != null) ? outcome.holdUntil() : now);
		row.setNotificationId(n.getId());
		row.setTemplateValues(values);
		row.setCreatedBy(n.getCreatedBy());
		if (channel == MessageChannel.WHATSAPP) {
			row.setMediaKey(n.getMediaKey());
		}
		if (r.type() == RecipientType.GUARDIAN) {
			row.setGuardianId(r.personId());
		}
		else if (r.type() == RecipientType.STAFF) {
			row.setStaffId(r.personId());
		}
		else {
			row.setEnquiryId(r.personId());
		}
		return row;
	}

	// The outbox body holds at most 1024 letters (WhatsApp's limit).
	private static String cut(String text) {
		return (text.length() <= 1024) ? text : text.substring(0, 1024);
	}

	private void markBroken(Long id) {
		transaction.executeWithoutResult(status -> notifications.markFailed(id,
				"Could not be prepared for sending. Nothing was sent; the error is in the server log.",
				Instant.now(clock).truncatedTo(ChronoUnit.MICROS)));
	}

	// SENDING with no QUEUED row left → SENT, or FAILED when nothing that was tried went out.
	private int closeFinished() {
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		List<Notification> done = notifications.claimFinished(MAX_PER_ROUND);
		for (Notification n : done) {
			long went = outbox.countOfNotification(n.getId(), WENT);
			long failed = outbox.countOfNotification(n.getId(), EnumSet.of(MessageStatus.FAILED));
			boolean allFailed = failed > 0 && went == 0;
			n.setStatus(allFailed ? NotificationStatus.FAILED : NotificationStatus.SENT);
			n.setFinishedAt(now);
			if (n.getRecipientCount() != null && n.getRecipientCount() == 0) {
				n.setNote("Nobody got it: everyone was skipped. See the report for the reasons.");
			}
			notifications.save(n);
			log.info("Notification {} closed: {} ({} went, {} failed)", n.getId(), n.getStatus(), went, failed);
		}
		return done.size();
	}

}

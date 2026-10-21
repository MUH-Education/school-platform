package com.muhjain.school.service.messaging;

import com.muhjain.school.exception.SmsSendException;
import com.muhjain.school.model.dto.response.SendContext;
import com.muhjain.school.model.dto.response.SendResult;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.entity.MessageTemplate;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.MessageProblem;
import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.enums.MessageStatus;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.model.repository.MessageOutboxRepository;
import com.muhjain.school.model.repository.MessageTemplateRepository;
import com.muhjain.school.service.notification.GuardChain;
import com.muhjain.school.service.notification.NotificationDelivery;
import com.muhjain.school.util.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sends the queued messages (rules 8 to 11 of the phase file). Real-life picture: the postman who empties the
 * school's letter box. {@link OutboxScheduler} calls {@link #runOnce()} every 5 seconds; a test calls it by hand.
 * <ul>
 * <li>Takes up to 50 QUEUED rows, oldest first, with {@code for update skip locked}. Two workers never send the
 * same row.</li>
 * <li>Sent: SENT with the provider id (or TEST_ONLY in test mode). Failed: {@code attempts + 1}, stays QUEUED;
 * after 3 failures FAILED.</li>
 * <li>A boarding SMS queued more than 2 hours ago is not sent: FAILED, "too old". A parent must not read "boarded at
 * 7:42" at noon.</li>
 * <li>A row is not taken before its {@code next_try_at} (quiet hours hold a notification row until 8:00).</li>
 * <li>Each row goes to the {@link ChannelSender} of its channel (SMS or WhatsApp). A WhatsApp row whose phone has no
 * WhatsApp fails at once and an SMS copy is queued instead (when the notification has an SMS text).</li>
 * <li>A notification row passes the last-second guards first ({@link GuardChain#beforeSend}): a fee reminder to a
 * family that has just paid becomes SKIPPED ALREADY_PAID instead of being sent.</li>
 * </ul>
 * One row that fails never stops the others.
 */
@Component
public class OutboxWorker {

	private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

	static final int BATCH_SIZE = 50;

	static final int MAX_ATTEMPTS = 3;

	static final Duration MAX_AGE = Duration.ofHours(2);

	static final String TOO_OLD = "too old";

	private final MessageOutboxRepository outbox;

	private final MessageTemplateRepository templates;

	private final Map<MessageChannel, ChannelSender> senders;

	private final GuardChain guardChain;

	private final NotificationDelivery delivery;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public OutboxWorker(MessageOutboxRepository outbox, MessageTemplateRepository templates,
			List<ChannelSender> senders, GuardChain guardChain, NotificationDelivery delivery,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.outbox = outbox;
		this.templates = templates;
		this.senders = byChannel(senders);
		this.guardChain = guardChain;
		this.delivery = delivery;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	// Built from the Spring beans, never a switch. Every channel has exactly one sender, or the app does not start.
	static Map<MessageChannel, ChannelSender> byChannel(List<ChannelSender> senders) {
		Map<MessageChannel, ChannelSender> map = new EnumMap<>(MessageChannel.class);
		for (ChannelSender sender : senders) {
			if (map.put(sender.channel(), sender) != null) {
				throw new IllegalStateException("Two senders for " + sender.channel());
			}
		}
		for (MessageChannel channel : MessageChannel.values()) {
			if (!map.containsKey(channel)) {
				throw new IllegalStateException("No ChannelSender for " + channel);
			}
		}
		return map;
	}

	/** One round. Returns how many rows it handled (sent, failed or marked too old). */
	public int runOnce() {
		Integer handled = transaction.execute(status -> {
			Instant now = Instant.now(clock);
			List<MessageOutbox> batch = outbox.claimQueued(now, BATCH_SIZE);
			for (MessageOutbox message : batch) {
				handle(message, now);
			}
			return batch.size();
		});
		return (handled == null) ? 0 : handled;
	}

	private void handle(MessageOutbox message, Instant now) {
		if (message.getPurpose() == MessagePurpose.BOARDING
				&& message.getCreatedAt().plus(MAX_AGE).isBefore(now)) {
			message.markFailed(TOO_OLD);
			return;
		}
		Optional<SkipReason> skip = guardChain.beforeSend(message);
		if (skip.isPresent()) {
			log.info("Message {} skipped at the last second: {}", message.getId(), skip.get());
			message.markSkipped(skip.get());
			return;
		}
		try {
			SendResult result = senders.get(message.getChannel()).send(message, contextFor(message));
			message.markDone(result.testOnly() ? MessageStatus.TEST_ONLY : MessageStatus.SENT, result.providerRef(),
					now);
		}
		catch (SmsSendException ex) {
			if (ex.problem() == MessageProblem.NOT_ON_WHATSAPP && message.getChannel() == MessageChannel.WHATSAPP) {
				// No use trying WhatsApp again: the SMS copy goes instead, in a later round.
				message.markNotOnWhatsApp(ex.getMessage());
				delivery.smsFallback(message).ifPresent(outbox::save);
				log.info("Message {} to {}: not on WhatsApp, SMS instead", message.getId(),
						PhoneNumbers.mask(message.getPhone()));
				return;
			}
			failedAttempt(message, ex);
		}
		catch (RuntimeException ex) {
			failedAttempt(message, ex);
		}
	}

	private static void failedAttempt(MessageOutbox message, Exception ex) {
		log.warn("{} {} to {} failed (attempt {}): {}", message.getChannel(), message.getId(),
				PhoneNumbers.mask(message.getPhone()), message.getAttempts() + 1, ex.getMessage());
		message.markAttemptFailed(ex.getMessage(), MAX_ATTEMPTS);
	}

	// A notification row: its template, values in order, media id. Any other row: the SMS (DLT) template id.
	private SendContext contextFor(MessageOutbox message) {
		if (message.getNotificationId() != null) {
			return delivery.contextFor(message);
		}
		// template_code has no foreign key any more (a bus code now has a boy row and a girl row), so this
		// is a best-effort lookup: it takes whichever row of the code has a provider id. A known gap until
		// the gender used to build the message is kept on the outbox row too (see docs/08-decisions.md).
		String providerTemplateId = (message.getTemplateCode() == null) ? null
				: templates.findByCode(message.getTemplateCode())
					.stream()
					.map(MessageTemplate::getProviderTemplateId)
					.filter(java.util.Objects::nonNull)
					.findFirst()
					.orElse(null);
		return SendContext.sms(providerTemplateId);
	}

}

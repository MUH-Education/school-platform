package com.muhjain.school.service.notification;

import com.muhjain.school.model.dto.response.FamilyContact;
import com.muhjain.school.model.dto.response.GuardOutcome;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.entity.MessageTemplate;
import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.RecipientType;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.model.enums.StudentStatus;
import com.muhjain.school.model.repository.MessageTemplateRepository;
import com.muhjain.school.model.repository.NotificationRepository;
import com.muhjain.school.service.student.GuardianService;
import com.muhjain.school.service.student.StudentQueryService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs every {@link RecipientGuard}, in order, twice (rule 19): {@link #check} at expand time (orders below
 * {@link RecipientGuard#LAST_SECOND}), and {@link #beforeSend} inside {@code OutboxWorker}, seconds before the
 * provider call (the last-second guards). The first guard that says no decides; the rest do not run.
 * Example: fee reminder expanded at 10:00, the Punia family paid at 9:30 → SKIPPED ALREADY_PAID; the Chauhan family
 * paid at 10:01 while the worker was busy → the last-second guard skips them too.
 */
@Component
public class GuardChain {

	private final List<RecipientGuard> expandGuards;

	private final List<RecipientGuard> lastSecondGuards;

	private final KindPolicies policies;

	private final MessageTemplateRepository templates;

	private final NotificationRepository notifications;

	private final GuardianService guardianService;

	private final StudentQueryService studentQuery;

	private final Clock clock;

	public GuardChain(List<RecipientGuard> guards, KindPolicies policies, MessageTemplateRepository templates,
			NotificationRepository notifications, GuardianService guardianService, StudentQueryService studentQuery,
			Clock clock) {
		List<RecipientGuard> sorted = guards.stream().sorted(Comparator.comparingInt(RecipientGuard::order)).toList();
		this.expandGuards = sorted.stream().filter(g -> g.order() < RecipientGuard.LAST_SECOND).toList();
		this.lastSecondGuards = sorted.stream().filter(g -> g.order() >= RecipientGuard.LAST_SECOND).toList();
		this.policies = policies;
		this.templates = templates;
		this.notifications = notifications;
		this.guardianService = guardianService;
		this.studentQuery = studentQuery;
		this.clock = clock;
	}

	/** The guards, in the order they run. For tests and for the docs. */
	public List<RecipientGuard> guards() {
		return java.util.stream.Stream.concat(expandGuards.stream(), lastSecondGuards.stream()).toList();
	}

	/** One context per run (one expand of one notification). */
	public GuardContext newContext(Notification notification) {
		Instant now = Instant.now(clock);
		MessageTemplate template = templates.findById(notification.getTemplateId()).orElse(null);
		return new GuardContext(now, LocalDate.ofInstant(now, clock.getZone()), template,
				policies.forKind(notification.getKind()));
	}

	/** The expand-time guards for one recipient. Never throws for a "no": the outcome carries the reason. */
	public GuardOutcome check(Recipient recipient, Notification notification, GuardContext context) {
		Candidate candidate = new Candidate(recipient, null);
		Optional<SkipReason> skip = run(expandGuards, candidate, notification, context);
		return new GuardOutcome(skip.orElse(null), candidate.values(), candidate.holdUntil());
	}

	/**
	 * The last-second guards for one outbox row, right before the provider call. A row that is not part of a
	 * notification (bus SMS, receipt) passes at once.
	 */
	public Optional<SkipReason> beforeSend(MessageOutbox row) {
		// Bus SMS and receipts are not notifications; the test copy to the sender is not checked either.
		if (row.getNotificationId() == null || row.getPurpose() == com.muhjain.school.model.enums.MessagePurpose.TEST) {
			return Optional.empty();
		}
		Optional<Notification> notification = notifications.findById(row.getNotificationId());
		if (notification.isEmpty()
				|| lastSecondGuards.stream().noneMatch(g -> g.appliesTo(notification.get().getKind()))) {
			return Optional.empty();
		}
		Candidate candidate = new Candidate(recipientOf(row), row.getCreatedAt());
		return run(lastSecondGuards, candidate, notification.get(), newContext(notification.get()));
	}

	private static Optional<SkipReason> run(List<RecipientGuard> guards, Candidate candidate, Notification n,
			GuardContext context) {
		for (RecipientGuard guard : guards) {
			if (!guard.appliesTo(n.getKind())) {
				continue;
			}
			Optional<SkipReason> skip = guard.check(candidate, n, context);
			if (skip.isPresent()) {
				return skip;
			}
		}
		return Optional.empty();
	}

	// The row as a recipient again. A family's children are the ACTIVE children whose family phone is this one
	// (the same rule as at expand time), so the fee numbers are the family's, not one child's.
	private Recipient recipientOf(MessageOutbox row) {
		if (row.getGuardianId() != null) {
			Long guardianId = row.getGuardianId();
			List<Long> linked = guardianService.childrenOf(guardianId);
			Map<Long, FamilyContact> contacts = guardianService.familyContacts(linked);
			List<Long> children = studentQuery.basics(linked)
				.values()
				.stream()
				.filter(s -> s.status() == StudentStatus.ACTIVE)
				.filter(s -> contacts.containsKey(s.id()) && guardianId.equals(contacts.get(s.id()).guardianId()))
				.map(s -> s.id())
				.toList();
			return new Recipient(RecipientType.GUARDIAN, guardianId, row.getPhone(), row.getChannel(), null, children,
					row.getTemplateValues());
		}
		RecipientType type = (row.getStaffId() != null) ? RecipientType.STAFF : RecipientType.ENQUIRY;
		Long personId = (row.getStaffId() != null) ? row.getStaffId() : row.getEnquiryId();
		return new Recipient(type, personId, row.getPhone(), row.getChannel(), null, List.of(),
				row.getTemplateValues());
	}

}

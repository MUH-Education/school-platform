package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.service.notification.Candidate;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import org.springframework.stereotype.Component;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.FamilyDues;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.repository.InboundMessageRepository;
import com.muhjain.school.model.repository.MessageOutboxRepository;
import com.muhjain.school.service.fee.FeeStatusService;

import java.time.Duration;
import java.util.Optional;

/**
 * 50, FEE_REMINDER: the family's dues are read again now, never from the draft (docs/10-notifications.md section 10,
 * steps 1 to 8). In order:
 * <ol>
 * <li>nothing due now → ALREADY_PAID (example: scheduled on the 9th, paid on the 10th at 9:30)</li>
 * <li>the family's credit covers what is due → CREDIT_COVERS</li>
 * <li>a receipt for this family today → ALREADY_PAID (the payment is being saved while we expand)</li>
 * <li>below the audience's {@code minMonthsLate} / {@code minAmount} now → BELOW_FILTER</li>
 * <li>promised to pay by a day that has not passed ({@code inbound_message.promised_until}) → PROMISED</li>
 * <li>a fee reminder to this phone in the last {@code skipRemindedWithinDays} days (default 7) → REMINDED_RECENTLY</li>
 * </ol>
 * Then the boxes are filled with the numbers of this moment: Amount due, Months, Months late, Late fee.
 */
@Component
public class FeeDueGuard implements RecipientGuard {

	private static final int DEFAULT_REMINDED_DAYS = 7;

	private final FeeStatusService feeStatusService;

	private final InboundMessageRepository inbound;

	private final MessageOutboxRepository outbox;

	public FeeDueGuard(FeeStatusService feeStatusService, InboundMessageRepository inbound,
			MessageOutboxRepository outbox) {
		this.feeStatusService = feeStatusService;
		this.inbound = inbound;
		this.outbox = outbox;
	}

	@Override
	public int order() {
		return 50;
	}

	@Override
	public boolean appliesTo(NotificationKind kind) {
		return kind == NotificationKind.FEE_REMINDER;
	}

	@Override
	public Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context) {
		Recipient r = candidate.recipient();
		AudienceFilter filter = notification.getAudience().filter();
		FamilyDues dues = feeStatusService.familyDues(r.studentIds());
		if (dues.amount().signum() <= 0) {
			return Optional.of(SkipReason.ALREADY_PAID);
		}
		if (feeStatusService.familyCredit(r.personId()).compareTo(dues.amount()) >= 0) {
			return Optional.of(SkipReason.CREDIT_COVERS);
		}
		if (feeStatusService.paidOn(r.personId(), context.today())) {
			return Optional.of(SkipReason.ALREADY_PAID);
		}
		if (!FeeDueValues.passes(dues, filter)) {
			return Optional.of(SkipReason.BELOW_FILTER);
		}
		boolean skipPromised = !Boolean.FALSE.equals(filter.skipPromised());
		if (skipPromised && inbound.existsByPhoneAndPromisedUntilGreaterThanEqual(r.phone(), context.today())) {
			return Optional.of(SkipReason.PROMISED);
		}
		int days = (filter.skipRemindedWithinDays() == null) ? DEFAULT_REMINDED_DAYS : filter.skipRemindedWithinDays();
		if (days > 0 && outbox.countByPhoneAndPurposeAndStatusInAndCreatedAtGreaterThanEqualAndNotificationIdNot(
				r.phone(), MessagePurpose.FEE_REMINDER, WeeklyCapGuard.COUNTED, context.now().minus(Duration.ofDays(days)),
				(notification.getId() == null) ? -1L : notification.getId()) > 0) {
			return Optional.of(SkipReason.REMINDED_RECENTLY);
		}
		candidate.putValues(FeeDueValues.of(dues));
		return Optional.empty();
	}

}

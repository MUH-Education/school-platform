package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.service.notification.Candidate;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import org.springframework.stereotype.Component;

import com.muhjain.school.service.fee.FeeStatusService;

import java.time.Duration;
import java.util.Optional;

/**
 * 90, FEE_REMINDER and URGENT, inside {@code OutboxWorker}, seconds before the provider call:
 * <ul>
 * <li>FEE_REMINDER: the family's dues are read once more (one query); nothing due now → ALREADY_PAID. Example: the
 * Chauhan family paid at 10:01 while the worker was busy with the 10:00 reminder.</li>
 * <li>URGENT: queued more than 2 hours ago → TOO_OLD (the same rule bus SMS have: "bus is late" at noon is wrong).</li>
 * </ul>
 */
@Component
public class LastSecondGuard implements RecipientGuard {

	public static final Duration URGENT_MAX_AGE = Duration.ofHours(2);

	private final FeeStatusService feeStatusService;

	public LastSecondGuard(FeeStatusService feeStatusService) {
		this.feeStatusService = feeStatusService;
	}

	@Override
	public int order() {
		return LAST_SECOND;
	}

	@Override
	public boolean appliesTo(NotificationKind kind) {
		return kind == NotificationKind.FEE_REMINDER || kind == NotificationKind.URGENT;
	}

	@Override
	public Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context) {
		if (notification.getKind() == NotificationKind.URGENT) {
			boolean tooOld = candidate.queuedAt() != null
					&& candidate.queuedAt().plus(URGENT_MAX_AGE).isBefore(context.now());
			return tooOld ? Optional.of(SkipReason.TOO_OLD) : Optional.empty();
		}
		boolean paid = feeStatusService.familyDues(candidate.recipient().studentIds()).amount().signum() <= 0;
		return paid ? Optional.of(SkipReason.ALREADY_PAID) : Optional.empty();
	}

}

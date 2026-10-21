package com.muhjain.school.service.notification;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;

import java.util.Optional;

/**
 * One check a recipient must pass before a message goes out (docs/10-notifications.md section 10). Guards are
 * Spring beans, collected by {@link GuardChain} and run in {@link #order()}. To add a check: one class.
 * A guard that says no never throws: it answers a {@link SkipReason}, and the row is saved as SKIPPED.
 * Example: {@code FeeDueGuard} answers ALREADY_PAID for a family that paid at 9:30 for a 10:00 reminder.
 */
public interface RecipientGuard {

	/** The guards from this order on run right before the provider call, in {@code OutboxWorker}. */
	int LAST_SECOND = 90;

	/** Lower runs first. 10 = cheap and general, 50 = per kind, 90 = last-second re-check. */
	int order();

	boolean appliesTo(NotificationKind kind);

	/**
	 * Empty = may go. A guard may also change the candidate: fill values (the live fee numbers) or hold it until
	 * later ({@link Candidate#holdUntil}).
	 */
	Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context);

}

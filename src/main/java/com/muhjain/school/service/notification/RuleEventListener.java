package com.muhjain.school.service.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.RuleFiring;
import com.muhjain.school.model.dto.response.SchoolEvent;
import com.muhjain.school.model.enums.RuleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Handles a {@link SchoolEvent} after the transaction that saved it has committed (docs/phases/phase-12, rule 4).
 * <ul>
 * <li>ADMISSION_SAVED → the WELCOME rule, to that family, next morning at 9:00. Run key {@code student:129}.</li>
 * <li>BUS_LATE → the BUS_LATE rule, to the families on that route today, with {@code {Minutes}}. Run key
 * {@code bus-late:4:2026-10-07:morning} (once per route per morning or evening).</li>
 * <li>PAYMENT_SAVED → the family's waiting fee reminders become SKIPPED ALREADY_PAID at once (no rule needed).</li>
 * </ul>
 * WELCOME and BUS_LATE run after the commit, on the threads of {@link RuleEventRunner}: the
 * caller's work is already saved, so a problem there (a template not approved) is only logged, and an admission never
 * fails because its welcome message could not be made. PAYMENT_SAVED runs just **before** the payment commits, in the
 * same transaction: one connection (twenty payments at once must not need forty), and a payment that rolls back
 * skips nothing.
 */
@Component
public class RuleEventListener {

	/** WELCOME is sent the next morning at this time (school time). */
	public static final LocalTime WELCOME_AT = LocalTime.of(9, 0);

	private static final Logger log = LoggerFactory.getLogger(RuleEventListener.class);

	private final RuleService ruleService;

	private final NotificationService notificationService;

	private final RuleEventRunner runner;

	private final Clock clock;

	public RuleEventListener(RuleService ruleService, NotificationService notificationService,
			RuleEventRunner runner, Clock clock) {
		this.ruleService = ruleService;
		this.notificationService = notificationService;
		this.runner = runner;
		this.clock = clock;
	}

	/** PAYMENT_SAVED, inside the payment's transaction, just before it commits. */
	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void beforeCommit(SchoolEvent event) {
		if (event.event() == RuleEvent.PAYMENT_SAVED) {
			notificationService.skipQueuedFeeReminders(event.guardianId());
		}
	}

	/** ADMISSION_SAVED and BUS_LATE, once the caller's transaction has committed, on the rule-event thread. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void afterCommit(SchoolEvent event) {
		if (event.event() != RuleEvent.ADMISSION_SAVED && event.event() != RuleEvent.BUS_LATE) {
			return;
		}
		// The time is taken now, not when the thread gets to it.
		RuleFiring firing = firing(event);
		runner.run(() -> {
			try {
				fireRules(event, firing);
			}
			catch (RuntimeException e) {
				log.error("Event {} could not be handled.", event.event(), e);
			}
		});
	}

	private void fireRules(SchoolEvent event, RuleFiring firing) {
		for (Long ruleId : ruleService.rulesFor(event.event())) {
			try {
				ruleService.fireAlone(ruleId, firing);
			}
			catch (ApiException e) {
				log.warn("Rule {} could not fire for {} (run {}): {}", ruleId, event.event(), firing.runKey(),
						e.getCode());
			}
		}
	}

	private RuleFiring firing(SchoolEvent event) {
		LocalDate today = LocalDate.now(clock);
		return switch (event.event()) {
			case ADMISSION_SAVED -> new RuleFiring("student:" + event.studentId(), Map.of(),
					families(List.of(event.guardianId())), today.plusDays(1).atTime(WELCOME_AT)
						.atZone(clock.getZone())
						.toInstant(),
					false);
			case BUS_LATE -> new RuleFiring(busLateRunKey(event.routeId(), ZonedDateTime.now(clock)),
					Map.of("Minutes", String.valueOf(event.minutes())), routes(List.of(event.routeId())), null, false);
			default -> throw new IllegalArgumentException("No rule fires on " + event.event());
		};
	}

	/** The run key of "running late": once per route per morning and per evening. Example: {@code bus-late:4:2026-10-07:morning}. */
	public static String busLateRunKey(Long routeId, ZonedDateTime at) {
		return "bus-late:" + routeId + ":" + at.toLocalDate() + ":"
				+ (at.toLocalTime().isBefore(LocalTime.NOON) ? "morning" : "evening");
	}

	private static AudienceFilter families(List<Long> guardianIds) {
		return new AudienceFilter(null, null, null, null, null, null, null, null, guardianIds, null);
	}

	private static AudienceFilter routes(List<Long> routeIds) {
		return new AudienceFilter(null, routeIds, null, null, null, null, null, null, null, null);
	}

}

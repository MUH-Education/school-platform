package com.muhjain.school.service.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.response.RuleFiring;
import com.muhjain.school.model.dto.response.RuleRunResult;
import com.muhjain.school.model.entity.NotificationRule;
import com.muhjain.school.model.enums.RuleRunStatus;
import com.muhjain.school.model.enums.RuleTrigger;
import com.muhjain.school.model.repository.NotificationRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Fires the CRON rules whose time has come (docs/phases/phase-12-automatic-rules.md, rule 3). {@link RuleSchedulerJob}
 * calls {@link #runOnce} every minute; tests call it by hand with the time they want.
 * <p>
 * For each rule that is on: the last fire time of its cron (school time) must be within the last 2 minutes.
 * Example: the fee reminder is due at 10:00. At 10:00:20 → fire. The server was down and comes back at 10:01 → still
 * fire. At 10:05 → too late, nothing (the owner can press "Run now" instead). The run key is the school day
 * ({@code 2026-10-10}), so a second call in the same window does nothing.
 * <p>
 * A rule with a date list in its fixed values (FESTIVAL: {@code {"dates": ["2027-03-04"], "Festival": {"2027-03-04":
 * "होली"}}}) fires only on those dates, with {@code {Festival}} = that day's name.
 */
@Service
public class RuleScheduler {

	/** How late a run may still happen after its time. */
	public static final Duration CATCH_UP = Duration.ofMinutes(2);

	private static final Logger log = LoggerFactory.getLogger(RuleScheduler.class);

	private final NotificationRuleRepository rules;

	private final RuleService ruleService;

	private final Clock clock;

	public RuleScheduler(NotificationRuleRepository rules, RuleService ruleService, Clock clock) {
		this.rules = rules;
		this.ruleService = ruleService;
		this.clock = clock;
	}

	/**
	 * One look at every CRON rule that is on. Each rule fires in its own transaction, so one broken rule (a template
	 * that is not approved) does not stop the others; it is logged.
	 *
	 * @return how many notifications were made
	 */
	public int runOnce(Instant now) {
		int made = 0;
		for (NotificationRule rule : rules.findByEnabledTrueAndTriggerTypeOrderByIdAsc(RuleTrigger.CRON)) {
			ZonedDateTime due = dueAt(rule.getCron(), now);
			if (due == null) {
				continue;
			}
			RuleFiring firing = firing(rule, due.toLocalDate());
			if (firing == null) {
				continue;
			}
			try {
				RuleRunResult result = ruleService.fire(rule.getId(), firing);
				if (result.status() == RuleRunStatus.CREATED) {
					made++;
				}
			}
			catch (ApiException e) {
				log.warn("Rule {} could not fire (run {}): {}", rule.getCode(), firing.runKey(), e.getCode());
			}
			catch (RuntimeException e) {
				log.error("Rule {} failed (run {}).", rule.getCode(), firing.runKey(), e);
			}
		}
		return made;
	}

	/**
	 * The fire time of the cron that falls in the last {@link #CATCH_UP} (up to and including now), or null.
	 * A cron that cannot be read is logged and never fires.
	 */
	ZonedDateTime dueAt(String cron, Instant now) {
		CronExpression expression;
		try {
			expression = CronExpression.parse(cron);
		}
		catch (IllegalArgumentException e) {
			log.warn("Rule cron \"{}\" cannot be read; skipped.", cron);
			return null;
		}
		ZonedDateTime at = now.atZone(clock.getZone());
		ZonedDateTime next = expression.next(at.minus(CATCH_UP));
		return (next != null && !next.isAfter(at)) ? next : null;
	}

	// The run of this day: key = the school day. A date-list rule only on its dates, with the day's name.
	private static RuleFiring firing(NotificationRule rule, LocalDate day) {
		String key = day.toString();
		Object dates = rule.getFixedValues().get("dates");
		if (!(dates instanceof List<?> list)) {
			return RuleFiring.of(key);
		}
		if (!list.contains(key)) {
			return null;
		}
		Map<String, String> values = (rule.getFixedValues().get("Festival") instanceof Map<?, ?> names
				&& names.get(key) instanceof String name) ? Map.of("Festival", name) : Map.of();
		return new RuleFiring(key, values, null, null, false);
	}

}

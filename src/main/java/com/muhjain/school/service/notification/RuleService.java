package com.muhjain.school.service.notification;

import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.dto.response.RuleFiring;
import com.muhjain.school.model.dto.response.RuleNotification;
import com.muhjain.school.model.dto.response.RuleRunResult;
import com.muhjain.school.model.entity.NotificationRule;
import com.muhjain.school.model.entity.NotificationRuleRun;
import com.muhjain.school.model.enums.RuleEvent;
import com.muhjain.school.model.enums.RuleRunStatus;
import com.muhjain.school.model.repository.NotificationRuleRepository;
import com.muhjain.school.model.repository.RuleRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Fires an automatic rule (docs/phases/phase-12-automatic-rules.md, rules 1 and 2). A rule never sends by itself:
 * it only makes a normal notification through {@link NotificationService#createFromRule}, and the Phase 11
 * dispatcher and guard chain do the rest. So the fee reminder on the 10th skips the families that paid, exactly like
 * a manual one.
 * <p>
 * The same run never happens twice: the run key is saved first ({@link RuleRunRepository#tryInsert}), in the same
 * transaction as the notification. If the app restarts at 10:00:30 and the scheduler fires again, the key is there
 * and nothing is made. If making the notification fails (template not approved), both roll back together.
 */
@Service
public class RuleService {

	private static final Logger log = LoggerFactory.getLogger(RuleService.class);

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private final NotificationRuleRepository rules;

	private final RuleRunRepository runs;

	private final NotificationService notificationService;

	private final Clock clock;

	public RuleService(NotificationRuleRepository rules, RuleRunRepository runs,
			NotificationService notificationService, Clock clock) {
		this.rules = rules;
		this.runs = runs;
		this.notificationService = notificationService;
		this.clock = clock;
	}

	/**
	 * Fires the rule once for this run key. Example: rule FEE_REMINDER_10TH with key {@code 2026-10-10} → CREATED,
	 * notification 57; the same call again → ALREADY_RAN.
	 * <p>
	 * OFF (nothing saved) when the rule is off (unless {@code manual}), has no template, or was never turned on by a
	 * user (the notification needs a "made by").
	 *
	 * @throws com.muhjain.school.exception.ApiException the checks of {@link NotificationService#createFromRule};
	 * then nothing is saved, not even the run key
	 */
	@Transactional
	public RuleRunResult fire(Long ruleId, RuleFiring firing) {
		return fireBy(ruleId, firing, null);
	}

	/**
	 * The same as {@link #fire}, with the notification "made by" this user instead of the one who turned the rule on.
	 * Used by "Run now" on the Settings screen.
	 */
	@Transactional
	public RuleRunResult fireBy(Long ruleId, RuleFiring firing, Long madeBy) {
		NotificationRule rule = rules.findLockedById(ruleId).orElse(null);
		if (rule == null || (!rule.isEnabled() && !firing.manual()) || rule.getTemplateId() == null) {
			return RuleRunResult.of(RuleRunStatus.OFF);
		}
		Long createdBy = (madeBy != null) ? madeBy : rule.getUpdatedBy();
		if (createdBy == null) {
			log.warn("Rule {} is on but nobody turned it on in Settings; not fired.", rule.getCode());
			return RuleRunResult.of(RuleRunStatus.OFF);
		}
		Instant now = Instant.now(clock);
		if (!runs.tryInsert(rule.getId(), firing.runKey(), now)) {
			return RuleRunResult.of(RuleRunStatus.ALREADY_RAN);
		}
		rule.setLastRunAt(now);

		Audience audience = (firing.filter() != null) ? new Audience(rule.getAudience().groups(), firing.filter())
				: rule.getAudience();
		// The owner's own delay wins over the event's time ("send 30 minutes after admission").
		Instant sendAt = (rule.getDelayMinutes() != null) ? now.plus(Duration.ofMinutes(rule.getDelayMinutes()))
				: firing.sendAt();
		RuleNotification asked = new RuleNotification(rule.getId(),
				rule.getName() + ", " + LocalDate.now(clock).format(DAY), rule.getKind(), rule.getTemplateId(),
				audience, fixedValues(rule, firing), sendAt, createdBy);
		Optional<Long> made = notificationService.createFromRule(asked);
		if (made.isEmpty()) {
			return RuleRunResult.of(RuleRunStatus.NOBODY);
		}
		runs.linkNotification(rule.getId(), firing.runKey(), made.get());
		log.info("Rule {} fired (run {}): notification {}.", rule.getCode(), firing.runKey(), made.get());
		return new RuleRunResult(RuleRunStatus.CREATED, made.get());
	}

	/**
	 * The same as {@link #fire}, in a transaction of its own. Used after another transaction has committed (an event
	 * handled by {@link RuleEventListener}), where Spring needs a new one to write anything.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public RuleRunResult fireAlone(Long ruleId, RuleFiring firing) {
		return fire(ruleId, firing);
	}

	/** The ids of the rules that are on for this event. Example: BUS_LATE → [6] when the BUS_LATE rule is on. */
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public List<Long> rulesFor(RuleEvent event) {
		return rules.findByEnabledTrueAndEventOrderByIdAsc(event).stream().map(NotificationRule::getId).toList();
	}

	/** True when a rule that is on for this event has run with this key already. */
	@Transactional(readOnly = true)
	public boolean alreadyRan(RuleEvent event, String runKey) {
		return rules.findByEnabledTrueAndEventOrderByIdAsc(event)
			.stream()
			.anyMatch(rule -> runs.existsById(new NotificationRuleRun.Key(rule.getId(), runKey)));
	}

	// The rule's own text values (a FESTIVAL date list is not one), then the values of this run on top.
	private static Map<String, String> fixedValues(NotificationRule rule, RuleFiring firing) {
		Map<String, String> values = new LinkedHashMap<>();
		rule.getFixedValues().forEach((box, value1) -> {
			if (value1 instanceof String text) {
				values.put(box, text);
			}
		});
		values.putAll(firing.fixedValues());
		return values;
	}

}

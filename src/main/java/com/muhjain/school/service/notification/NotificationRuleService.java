package com.muhjain.school.service.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.NotificationRuleRequest;
import com.muhjain.school.model.dto.response.FestivalDay;
import com.muhjain.school.model.dto.response.NotificationRuleResponse;
import com.muhjain.school.model.dto.response.RuleFiring;
import com.muhjain.school.model.dto.response.RuleRunResult;
import com.muhjain.school.model.entity.MessageTemplate;
import com.muhjain.school.model.entity.NotificationRule;
import com.muhjain.school.model.enums.AuditAction;
import com.muhjain.school.model.enums.RuleTrigger;
import com.muhjain.school.model.enums.TemplateApproval;
import com.muhjain.school.model.enums.TemplateKind;
import com.muhjain.school.model.repository.MessageTemplateRepository;
import com.muhjain.school.model.repository.NotificationRepository;
import com.muhjain.school.model.repository.NotificationRuleRepository;
import com.muhjain.school.service.audit.AuditService;
import com.muhjain.school.service.user.UserService;
import com.muhjain.school.util.RuleSchedules;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The Settings screen's "Automatic messages" table (docs/phases/phase-12-automatic-rules.md, rule 8): the list, a
 * change (switch on/off, template, audience, time, festival dates) and "Run now". Firing itself is {@link RuleService}.
 * <ul>
 * <li>A rule can be on only with a template of its kind that WhatsApp approved, and with somebody to send to.</li>
 * <li>The time is one of three choices (every day / week / month at); the server builds the cron.</li>
 * <li>Every change is an audit row. The user who changes a rule becomes its "made by" for the next runs.</li>
 * </ul>
 */
@Service
public class NotificationRuleService {

	static final String ENTITY = "NOTIFICATION_RULE";

	/** "Last month" on the Settings table = the last 30 days. */
	public static final Duration LAST_MONTH = Duration.ofDays(30);

	private final NotificationRuleRepository rules;

	private final NotificationRepository notifications;

	private final MessageTemplateRepository templates;

	private final KindPolicies policies;

	private final RuleService ruleService;

	private final UserService userService;

	private final AuditService auditService;

	private final Clock clock;

	public NotificationRuleService(NotificationRuleRepository rules, NotificationRepository notifications,
			MessageTemplateRepository templates, KindPolicies policies, RuleService ruleService,
			UserService userService, AuditService auditService, Clock clock) {
		this.rules = rules;
		this.notifications = notifications;
		this.templates = templates;
		this.policies = policies;
		this.ruleService = ruleService;
		this.userService = userService;
		this.auditService = auditService;
		this.clock = clock;
	}

	/** Every rule, in the order V15 made them, with the last run and the last 30 days' counts. */
	@Transactional(readOnly = true)
	public List<NotificationRuleResponse> list() {
		List<NotificationRule> all = rules.findAllByOrderByIdAsc();
		Map<Long, long[]> counts = new HashMap<>();
		for (Object[] row : notifications.countByRuleSince(Instant.now(clock).minus(LAST_MONTH))) {
			counts.put((Long) row[0], new long[] { ((Number) row[1]).longValue(), ((Number) row[2]).longValue() });
		}
		Map<Long, String> templateNames = new HashMap<>();
		templates.findAllById(all.stream().map(NotificationRule::getTemplateId).filter(Objects::nonNull).toList())
			.forEach(t -> templateNames.put(t.getId(), t.getName()));
		Map<Long, String> userNames = userService
			.displayNames(all.stream().map(NotificationRule::getUpdatedBy).filter(Objects::nonNull).distinct().toList());
		return all.stream()
			.map(r -> toResponse(r, templateNames.get(r.getTemplateId()),
					(r.getUpdatedBy() == null) ? null : userNames.get(r.getUpdatedBy()),
					counts.getOrDefault(r.getId(), new long[] { 0, 0 })))
			.toList();
	}

	/**
	 * Changes what is sent (null = keep). Example: {@code {"enabled": true}} on the fee reminder → on.
	 *
	 * @param userId the logged-in user, from the token; becomes the rule's "made by"
	 * @throws ApiException 404 NOT_FOUND, 409 TEMPLATE_NOT_FOR_KIND, 409 RULE_NEEDS_TEMPLATE, 409
	 * TEMPLATE_NOT_APPROVED, 409 RULE_NEEDS_AUDIENCE, 409 AUDIENCE_NOT_ALLOWED, 400 VALIDATION (a field this rule does
	 * not have, a schedule that does not fit)
	 */
	@Transactional
	public NotificationRuleResponse update(Long id, NotificationRuleRequest request, Long userId) {
		NotificationRule rule = rules.findLockedById(id).orElseThrow(NotificationRuleService::notFound);
		List<String> changes = new ArrayList<>();
		boolean cron = rule.getTriggerType() == RuleTrigger.CRON;

		if (request.templateId() != null && !request.templateId().equals(rule.getTemplateId())) {
			MessageTemplate template = templateFor(rule, request.templateId());
			rule.setTemplateId(template.getId());
			changes.add("template \"" + template.getName() + "\"");
		}
		if (request.audience() != null) {
			if (!cron) {
				throw ApiException.validation("audience", "comes from the event (the family or the route)");
			}
			policies.checkAudience(rule.getKind(), request.audience());
			rule.setAudience(request.audience());
			changes.add("audience");
		}
		if (request.schedule() != null) {
			if (!cron) {
				throw ApiException.validation("schedule", "this rule goes when something happens, not at a time");
			}
			String newCron = RuleSchedules.toCron(request.schedule());
			if (!newCron.equals(rule.getCron())) {
				rule.setCron(newCron);
				changes.add("time");
			}
		}
		if (request.delayMinutes() != null) {
			if (cron) {
				throw ApiException.validation("delayMinutes", "only for a rule that goes when something happens");
			}
			rule.setDelayMinutes(request.delayMinutes());
			changes.add("delay " + request.delayMinutes() + " min");
		}
		if (request.festivals() != null) {
			if (!(rule.getFixedValues().get("dates") instanceof List<?>)) {
				throw ApiException.validation("festivals", "this rule has no festival dates");
			}
			rule.setFixedValues(festivalValues(request.festivals()));
			changes.add(request.festivals().size() + " festival dates");
		}
		if (request.enabled() != null && request.enabled() != rule.isEnabled()) {
			rule.setEnabled(request.enabled());
			changes.add(request.enabled() ? "turned on" : "turned off");
		}
		if (rule.isEnabled()) {
			requireReady(rule);
		}
		if (changes.isEmpty()) {
			return toResponse(rule);
		}
		rule.setUpdatedBy(userId);
		rules.saveAndFlush(rule);
		auditService.record(ENTITY, rule.getId(), AuditAction.UPDATED,
				"Automatic message \"" + rule.getName() + "\": " + String.join(", ", changes) + ".",
				Map.of("code", rule.getCode(), "enabled", rule.isEnabled()));
		return toResponse(rule);
	}

	/**
	 * "Run now": fires the rule once, now, made by this user, even when it is off (it still needs a template).
	 * Run key {@code manual:<time>}, so it never blocks the next timed run.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 RULE_NEEDS_EVENT (WELCOME, BUS_LATE: there is no family or route to
	 * send to), 409 RULE_NEEDS_TEMPLATE, and the checks of {@code NotificationService.createFromRule}
	 */
	@Transactional
	public RuleRunResult runNow(Long id, Long userId) {
		NotificationRule rule = rules.findById(id).orElseThrow(NotificationRuleService::notFound);
		if (rule.getTriggerType() == RuleTrigger.EVENT) {
			throw new ApiException(HttpStatus.CONFLICT, "RULE_NEEDS_EVENT",
					"This message goes when something happens (an admission, a late bus). It cannot be run by hand.");
		}
		if (rule.getTemplateId() == null) {
			throw needsTemplate();
		}
		Instant now = Instant.now(clock);
		Map<String, String> values = new LinkedHashMap<>();
		String today = LocalDate.now(clock).toString();
		if (rule.getFixedValues().get("Festival") instanceof Map<?, ?> names && names.get(today) instanceof String name) {
			values.put("Festival", name);
		}
		RuleRunResult result = ruleService.fireBy(rule.getId(),
				new RuleFiring("manual:" + now, values, null, null, true), userId);
		auditService.record(ENTITY, rule.getId(), AuditAction.UPDATED,
				"Automatic message \"" + rule.getName() + "\" run by hand: " + result.status().name().toLowerCase()
						+ ((result.notificationId() != null) ? ", notification " + result.notificationId() : "") + ".",
				Map.of("code", rule.getCode(), "status", result.status().name()));
		return result;
	}

	// A template that is in use and of the rule's kind.
	private MessageTemplate templateFor(NotificationRule rule, Long templateId) {
		return templates.findById(templateId)
			.filter(t -> t.getKind() == TemplateKind.of(rule.getKind()) && t.isActive())
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "TEMPLATE_NOT_FOR_KIND",
					"Pick a " + rule.getKind().name().toLowerCase().replace('_', ' ') + " template that is in use."));
	}

	// What a rule needs to be on: a template WhatsApp approved (or that may be sent as free text) and somebody to
	// send to.
	private void requireReady(NotificationRule rule) {
		if (rule.getTemplateId() == null) {
			throw needsTemplate();
		}
		MessageTemplate template = templateFor(rule, rule.getTemplateId());
		if (template.getApproval() != TemplateApproval.APPROVED
				&& !policies.forKind(rule.getKind()).freeTextInsideWindow()) {
			throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_NOT_APPROVED",
					"WhatsApp has not approved this template yet.");
		}
		if (rule.getAudience().groups().isEmpty()) {
			throw new ApiException(HttpStatus.CONFLICT, "RULE_NEEDS_AUDIENCE", "Pick who gets this message first.");
		}
	}

	private static Map<String, Object> festivalValues(List<FestivalDay> festivals) {
		Map<String, String> names = new TreeMap<>();
		for (FestivalDay day : festivals) {
			names.put(day.date().toString(), day.name().strip());
		}
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("dates", new ArrayList<>(names.keySet()));
		values.put("Festival", names);
		return values;
	}

	private static List<FestivalDay> festivalsOf(NotificationRule rule) {
		if (!(rule.getFixedValues().get("dates") instanceof List<?> dates)) {
			return null;
		}
		Map<?, ?> names = (rule.getFixedValues().get("Festival") instanceof Map<?, ?> map) ? map : Map.of();
		return dates.stream()
			.map(String::valueOf)
			.map(d -> new FestivalDay(LocalDate.parse(d), (names.get(d) == null) ? "" : String.valueOf(names.get(d))))
			.toList();
	}

	private NotificationRuleResponse toResponse(NotificationRule rule) {
		String templateName = (rule.getTemplateId() == null) ? null
				: templates.findById(rule.getTemplateId()).map(MessageTemplate::getName).orElse(null);
		String userName = (rule.getUpdatedBy() == null) ? null
				: userService.displayNames(List.of(rule.getUpdatedBy())).get(rule.getUpdatedBy());
		long[] counts = new long[] { 0, 0 };
		for (Object[] row : notifications.countByRuleSince(Instant.now(clock).minus(LAST_MONTH))) {
			if (rule.getId().equals(row[0])) {
				counts = new long[] { ((Number) row[1]).longValue(), ((Number) row[2]).longValue() };
			}
		}
		return toResponse(rule, templateName, userName, counts);
	}

	private NotificationRuleResponse toResponse(NotificationRule r, String templateName, String userName,
			long[] counts) {
		return new NotificationRuleResponse(r.getId(), r.getCode(), r.getName(), r.getKind(), r.getTemplateId(),
				templateName, r.getAudience(), r.getTriggerType(), r.getCron(), RuleSchedules.fromCron(r.getCron()),
				r.getEvent(), festivalsOf(r), r.getDelayMinutes(), r.isEnabled(), at(r.getLastRunAt()),
				r.getUpdatedBy(), userName, counts[0], counts[1]);
	}

	private OffsetDateTime at(Instant instant) {
		return (instant == null) ? null : instant.atZone(clock.getZone()).toOffsetDateTime();
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This automatic message does not exist.");
	}

	private static ApiException needsTemplate() {
		return new ApiException(HttpStatus.CONFLICT, "RULE_NEEDS_TEMPLATE", "Pick a template for this message first.");
	}

}

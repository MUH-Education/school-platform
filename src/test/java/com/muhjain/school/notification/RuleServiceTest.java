package com.muhjain.school.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.PaymentRequest;
import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.PaymentLine;
import com.muhjain.school.model.dto.response.RuleFiring;
import com.muhjain.school.model.dto.response.RuleRunResult;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.FeeHead;
import com.muhjain.school.model.enums.PaymentMode;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.model.enums.RuleRunStatus;
import com.muhjain.school.service.fee.PaymentService;
import com.muhjain.school.service.notification.NotificationDispatcher;
import com.muhjain.school.service.notification.RuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code RuleService.fire} and {@code NotificationService.createFromRule} (task 12.3), with the real Phase 11
 * dispatcher and guard chain, on the small school of {@link NotificationTestBase} (7 Oct 2026, 10 am).
 */
class RuleServiceTest extends NotificationTestBase {

	@Autowired
	private RuleService ruleService;

	@Autowired
	private NotificationDispatcher dispatcher;

	@Autowired
	private PaymentService paymentService;

	private AppUser owner;

	@BeforeEach
	void owner() {
		owner = addUser("+919812340001", Role.OWNER);
	}

	private long template(String kind, String body, String approval) {
		return jdbc.queryForObject("insert into message_template (kind, name, channel, body, approved_body, approval) "
				+ "values (?, 'T', 'WHATSAPP', ?, ?, ?) returning id", Long.class, kind, body,
				"APPROVED".equals(approval) ? body : null, approval);
	}

	private long rule(String code) {
		return jdbc.queryForObject("select id from notification_rule where code = ?", Long.class, code);
	}

	/** Picks the template and turns the rule on, as the owner would in Settings. */
	private long turnOn(String code, long templateId) {
		jdbc.update("update notification_rule set template_id = ?, enabled = true, updated_by = ? where code = ?",
				templateId, owner.getId(), code);
		return rule(code);
	}

	private RuleRunResult fire(long ruleId, String runKey) {
		return ruleService.fire(ruleId, RuleFiring.of(runKey));
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	@Test
	void feeReminderRuleCreatesNotificationThatSkipsPaidFamilies() {
		long fee = turnOn("FEE_REMINDER_10TH", template("FEE_REMINDER", "{Parent name} जी, {Amount due} बाकी है।",
				"APPROVED"));
		// Sharma paid this month today, but still owes August and September: the guard skips him.
		jdbc.update("insert into fee_plan (student_id, session_id, from_month, school_fee_per_month, created_by) "
				+ "values (?, ?, date '2026-04-01', 3200, ?)", dev, sessionId, owner.getId());
		paymentService.record(dev, new PaymentRequest(null, PaymentMode.CASH, null,
				List.of(new PaymentLine(FeeHead.SCHOOL, new BigDecimal("3200")))), owner.getId());

		RuleRunResult result = fire(fee, "2026-10-10");
		assertThat(result.status()).isEqualTo(RuleRunStatus.CREATED);
		assertThat(jdbc.queryForMap("select kind, status, source, rule_id, name, created_by from notification "
				+ "where id = ?", result.notificationId()))
			.containsEntry("kind", "FEE_REMINDER")
			.containsEntry("status", "SENDING")
			.containsEntry("source", "RULE")
			.containsEntry("rule_id", fee)
			.containsEntry("name", "Fee reminder 10th, 7 Oct 2026")
			.containsEntry("created_by", owner.getId());

		// From here it is Phase 11: the dispatcher writes the rows and the guard chain checks each one.
		dispatcher.runOnce();
		List<Map<String, Object>> rows = jdbc.queryForList("select phone, status, skip_reason from message_outbox "
				+ "where notification_id = ? order by phone", result.notificationId());
		assertThat(rows).extracting(r -> r.get("phone")).containsExactly("+919811100001", "+919811100004");
		assertThat(rows.get(0)).containsEntry("status", "QUEUED");
		assertThat(rows.get(1)).containsEntry("status", "SKIPPED").containsEntry("skip_reason", "ALREADY_PAID");
		assertThat(count("select count(*) from notification_rule_run where rule_id = ? and notification_id = ?", fee,
				result.notificationId()))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("select last_run_at from notification_rule where id = ?", Instant.class, fee))
			.isEqualTo(Instant.parse("2026-10-07T04:30:00Z"));
	}

	@Test
	void theSameRunKeyFiresOnce() {
		long fee = turnOn("FEE_REMINDER_10TH", template("FEE_REMINDER", "{Amount due} बाकी है।", "APPROVED"));

		assertThat(fire(fee, "2026-10-10").status()).isEqualTo(RuleRunStatus.CREATED);
		assertThat(fire(fee, "2026-10-10")).isEqualTo(RuleRunResult.of(RuleRunStatus.ALREADY_RAN));
		assertThat(count("select count(*) from notification where rule_id = ?", fee)).isEqualTo(1);
	}

	@Test
	void disabledRuleNeverFires() {
		long template = template("FEE_REMINDER", "{Amount due} बाकी है।", "APPROVED");
		long fee = rule("FEE_REMINDER_10TH");
		// Off and no template, as V15 made it.
		assertThat(fire(fee, "2026-10-10").status()).isEqualTo(RuleRunStatus.OFF);
		// A template, but still off.
		jdbc.update("update notification_rule set template_id = ?, updated_by = ? where id = ?", template,
				owner.getId(), fee);
		assertThat(fire(fee, "2026-10-10").status()).isEqualTo(RuleRunStatus.OFF);
		// On, but nobody turned it on in Settings (no "made by").
		jdbc.update("update notification_rule set enabled = true, updated_by = null where id = ?", fee);
		assertThat(fire(fee, "2026-10-10").status()).isEqualTo(RuleRunStatus.OFF);

		assertThat(count("select count(*) from notification")).isZero();
		assertThat(count("select count(*) from notification_rule_run")).isZero();
	}

	@Test
	void runNowFiresARuleThatIsOff() {
		long fee = turnOn("FEE_REMINDER_10TH", template("FEE_REMINDER", "{Amount due} बाकी है।", "APPROVED"));
		jdbc.update("update notification_rule set enabled = false where id = ?", fee);

		RuleRunResult result = ruleService.fire(fee,
				new RuleFiring("manual:2026-10-07T04:30:00Z", Map.of(), null, null, true));
		assertThat(result.status()).isEqualTo(RuleRunStatus.CREATED);
	}

	@Test
	void nobodyToSendToSavesTheRunButNoNotification() {
		// No child has a birthday on 7 Oct (all are born on 14 May).
		long birthday = turnOn("BIRTHDAY", template("GREETING", "{Child name} को जन्मदिन मुबारक!", "APPROVED"));

		assertThat(fire(birthday, "2026-10-07")).isEqualTo(RuleRunResult.of(RuleRunStatus.NOBODY));
		assertThat(count("select count(*) from notification")).isZero();
		assertThat(count("select count(*) from notification_rule_run where rule_id = ? and notification_id is null",
				birthday))
			.isEqualTo(1);
	}

	@Test
	void aTemplateNotApprovedSavesNothingNotEvenTheRun() {
		long fee = turnOn("FEE_REMINDER_10TH", template("FEE_REMINDER", "{Amount due} बाकी है।", "WAITING"));

		assertThatThrownBy(() -> fire(fee, "2026-10-10")).isInstanceOf(ApiException.class)
			.hasFieldOrPropertyWithValue("code", "TEMPLATE_NOT_APPROVED");
		assertThat(count("select count(*) from notification")).isZero();
		// Not saved, so once the template is approved the same run can still happen.
		assertThat(count("select count(*) from notification_rule_run")).isZero();
	}

	@Test
	void aRunCanGiveTheRouteTheValuesAndALaterTime() {
		long busLate = turnOn("BUS_LATE", template("URGENT", "{Route} की बस {Minutes} मिनट देर से है।", "APPROVED"));
		AudienceFilter route = new AudienceFilter(null, List.of(route4), null, null, null, null, null, null, null,
				null);
		Instant tomorrowNine = Instant.parse("2026-10-08T03:30:00Z");

		RuleRunResult result = ruleService.fire(busLate,
				new RuleFiring("bus-late:" + route4 + ":2026-10-07", Map.of("Minutes", "20"), route, tomorrowNine,
						false));
		assertThat(result.status()).isEqualTo(RuleRunStatus.CREATED);
		Map<String, Object> made = jdbc.queryForMap("select status, scheduled_at, fixed_values::text as fv, "
				+ "audience -> 'filter' -> 'routeIds' ->> 0 as route from notification where id = ?",
				result.notificationId());
		assertThat(made).containsEntry("status", "SCHEDULED")
			.containsEntry("fv", "{\"Minutes\": \"20\"}")
			.containsEntry("route", String.valueOf(route4));
		assertThat(((java.sql.Timestamp) made.get("scheduled_at")).toInstant()).isEqualTo(tomorrowNine);
	}

	@Test
	void aBoxWithoutAValueIsRefused() {
		long busLate = turnOn("BUS_LATE", template("URGENT", "बस {Minutes} मिनट देर से है।", "APPROVED"));
		AudienceFilter route = new AudienceFilter(null, List.of(route4), null, null, null, null, null, null, null,
				null);

		assertThatThrownBy(() -> ruleService.fire(busLate, new RuleFiring("bus-late:x", Map.of(), route, null, false)))
			.isInstanceOf(ApiException.class)
			.hasFieldOrPropertyWithValue("code", "VALIDATION");
		assertThat(count("select count(*) from notification_rule_run")).isZero();
	}

}

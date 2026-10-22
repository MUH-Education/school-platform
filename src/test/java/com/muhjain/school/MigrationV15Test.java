package com.muhjain.school;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The database rules of V15__notification_rules.sql. They hold even if the Java code has a bug. */
class MigrationV15Test extends AbstractIntegrationTest {

	private long ruleId(String code) {
		return jdbc.queryForObject("select id from notification_rule where code = ?", Long.class, code);
	}

	private long addTemplate(String kind) {
		return jdbc.queryForObject("insert into message_template (kind, name, channel, body, approval) "
				+ "values (?, 'Birthday wish', 'WHATSAPP', 'जन्मदिन मुबारक {Student name}', 'APPROVED') returning id",
				Long.class, kind);
	}

	private long addRuleNotification(long ruleId) {
		return jdbc.queryForObject("insert into notification (kind, name, template_id, body, audience, source, rule_id, "
				+ "created_by) values ('GREETING', 'Birthday wish', ?, 'x', '{\"groups\":[\"BIRTHDAY_TODAY\"]}', 'RULE', "
				+ "?, ?) returning id", Long.class, addTemplate("GREETING"), ruleId, anyUserId());
	}

	private void addRule(String code, String trigger, String cron, String event) {
		jdbc.update("insert into notification_rule (code, name, kind, audience, trigger_type, cron, event) "
				+ "values (?, 'Test rule', 'NOTICE', '{}', ?, ?, ?)", code, trigger, cron, event);
	}

	private static void refused(Runnable sql) {
		assertThatThrownBy(sql::run).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void theEightRulesAreSeededOffAndWithoutTemplate() {
		assertThat(jdbc.queryForList("select code from notification_rule order by code", String.class))
			.containsExactly("BIRTHDAY", "BUS_LATE", "DOCUMENTS_MISSING_MONDAY", "FEE_REMINDER_10TH",
					"FEE_REMINDER_20TH", "FESTIVAL", "NO_MORNING_TAP", "WELCOME");
		assertThat(jdbc.queryForObject("select count(*) from notification_rule where enabled or template_id is not null",
				Integer.class))
			.isZero();
		assertThat(jdbc.queryForMap("select kind, trigger_type, cron from notification_rule "
				+ "where code = 'FEE_REMINDER_10TH'"))
			.containsEntry("kind", "FEE_REMINDER")
			.containsEntry("trigger_type", "CRON")
			.containsEntry("cron", "0 0 10 10 * *");
		assertThat(jdbc.queryForMap("select trigger_type, event from notification_rule where code = 'WELCOME'"))
			.containsEntry("trigger_type", "EVENT")
			.containsEntry("event", "ADMISSION_SAVED");
		assertThat(jdbc.queryForObject("select audience -> 'groups' ->> 0 from notification_rule where code = 'BIRTHDAY'",
				String.class))
			.isEqualTo("BIRTHDAY_TODAY");
	}

	@Test
	void aRuleIsOnOnlyWithATemplate() {
		long id = ruleId("BIRTHDAY");
		refused(() -> jdbc.update("update notification_rule set enabled = true where id = ?", id));
		jdbc.update("update notification_rule set template_id = ?, enabled = true where id = ?", addTemplate("GREETING"),
				id);
		// The template cannot be taken away while the rule is on.
		refused(() -> jdbc.update("update notification_rule set template_id = null where id = ?", id));
		refused(() -> jdbc.update("update notification_rule set template_id = 999999 where id = ?", id));
	}

	@Test
	void cronRulesHaveACronAndEventRulesAnEvent() {
		addRule("EVERY_MONDAY", "CRON", "0 0 10 * * MON", null);
		addRule("ON_PAYMENT", "EVENT", null, "PAYMENT_SAVED");
		refused(() -> addRule("NO_CRON", "CRON", null, null));
		refused(() -> addRule("NO_EVENT", "EVENT", null, null));
		refused(() -> addRule("BOTH", "CRON", "0 0 10 * * MON", "BUS_LATE"));
		refused(() -> addRule("UNKNOWN_EVENT", "EVENT", null, "EXAM_RESULT"));
		refused(() -> addRule("UNKNOWN_TRIGGER", "TIMER", "0 0 10 * * MON", null));
		refused(() -> addRule("BIRTHDAY", "CRON", "0 0 6 * * *", null));      // the code is unique
		refused(() -> jdbc.update("update notification_rule set kind = 'COST' where code = 'BIRTHDAY'"));
		refused(() -> jdbc.update("update notification_rule set delay_minutes = -5 where code = 'WELCOME'"));
	}

	@Test
	void theSameRunNeverHappensTwice() {
		long rule = ruleId("FEE_REMINDER_10TH");
		String sql = "insert into notification_rule_run (rule_id, run_key) values (?, ?)";
		jdbc.update(sql, rule, "2026-10-10");
		refused(() -> jdbc.update(sql, rule, "2026-10-10"));
		// Another day, or another rule on the same day, is a new run.
		jdbc.update(sql, rule, "2026-11-10");
		jdbc.update(sql, ruleId("BIRTHDAY"), "2026-10-10");
		refused(() -> jdbc.update(sql, 999999L, "2026-10-10"));
	}

	@Test
	void aNotificationPointsAtARealRule() {
		long rule = ruleId("BIRTHDAY");
		long notification = addRuleNotification(rule);
		jdbc.update("insert into notification_rule_run (rule_id, run_key, notification_id) values (?, ?, ?)", rule,
				"2026:student:129", notification);
		refused(() -> addRuleNotification(999999L));
	}

	@Test
	void noMoneyColumns() {
		assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name in "
				+ "('notification_rule', 'notification_rule_run') and (column_name like '%cost%' "
				+ "or column_name like '%price%' or column_name like '%paise%' or column_name like '%budget%')",
				Integer.class))
			.isZero();
	}

}

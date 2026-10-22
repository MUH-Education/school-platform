package com.muhjain.school.notification;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.model.entity.NotificationRule;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.RuleEvent;
import com.muhjain.school.model.enums.RuleTrigger;
import com.muhjain.school.model.repository.NotificationRuleRepository;
import com.muhjain.school.model.repository.RuleRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The rule tables read and written from Java (task 12.2). */
class RuleRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private NotificationRuleRepository rules;

	@Autowired
	private RuleRunRepository runs;

	@Autowired
	private TransactionTemplate tx;

	private static final Instant TEN_OCT = Instant.parse("2026-10-10T04:30:00Z");

	@Test
	void everySeededRuleLoads() {
		List<NotificationRule> all = rules.findAllByOrderByIdAsc();
		assertThat(all).extracting(NotificationRule::getCode)
			.containsExactly("FEE_REMINDER_10TH", "FEE_REMINDER_20TH", "BIRTHDAY", "FESTIVAL", "WELCOME", "BUS_LATE",
					"NO_MORNING_TAP", "DOCUMENTS_MISSING_MONDAY");
		assertThat(all).allSatisfy(rule -> {
			assertThat(rule.isEnabled()).isFalse();
			assertThat(rule.getTemplateId()).isNull();
		});

		NotificationRule fee = rules.findByCode("FEE_REMINDER_10TH").orElseThrow();
		assertThat(fee.getKind()).isEqualTo(NotificationKind.FEE_REMINDER);
		assertThat(fee.getTriggerType()).isEqualTo(RuleTrigger.CRON);
		assertThat(fee.getCron()).isEqualTo("0 0 10 10 * *");
		assertThat(fee.getAudience().groups()).containsExactly(AudienceGroup.FAMILIES_WITH_DUES);
		assertThat(fee.getAudience().filter().skipPromised()).isTrue();

		assertThat(rules.findByCode("BIRTHDAY").orElseThrow().getAudience().groups())
			.containsExactly(AudienceGroup.BIRTHDAY_TODAY);
		assertThat(rules.findByCode("NO_MORNING_TAP").orElseThrow().getAudience().groups())
			.containsExactly(AudienceGroup.BUS_CHILDREN_WITHOUT_TAP_TODAY);
		NotificationRule welcome = rules.findByCode("WELCOME").orElseThrow();
		assertThat(welcome.getTriggerType()).isEqualTo(RuleTrigger.EVENT);
		assertThat(welcome.getEvent()).isEqualTo(RuleEvent.ADMISSION_SAVED);
		assertThat(rules.findByCode("FESTIVAL").orElseThrow().getFixedValues()).containsEntry("dates", List.of())
			.containsEntry("Festival", Map.of());
	}

	@Test
	void festivalDatesAndNamesAreSavedAndReadBack() {
		Long id = rules.findByCode("FESTIVAL").orElseThrow().getId();
		tx.executeWithoutResult(status -> rules.findById(id)
			.orElseThrow()
			.setFixedValues(Map.of("dates", List.of("2027-03-04"), "Festival", Map.of("2027-03-04", "होली"))));

		assertThat(rules.findById(id).orElseThrow().getFixedValues())
			.containsEntry("dates", List.of("2027-03-04"))
			.containsEntry("Festival", Map.of("2027-03-04", "होली"));
	}

	@Test
	void onlyEnabledRulesAreFoundForTheSchedulerAndEvents() {
		long template = jdbc.queryForObject("insert into message_template (kind, name, channel, body, approval) "
				+ "values ('URGENT', 'Bus late', 'WHATSAPP', 'बस {Minutes} मिनट देर से है', 'APPROVED') returning id",
				Long.class);
		assertThat(rules.findByEnabledTrueAndEventOrderByIdAsc(RuleEvent.BUS_LATE)).isEmpty();
		jdbc.update("update notification_rule set template_id = ?, enabled = true where code = 'BUS_LATE'", template);

		assertThat(rules.findByEnabledTrueAndEventOrderByIdAsc(RuleEvent.BUS_LATE)).extracting(NotificationRule::getCode)
			.containsExactly("BUS_LATE");
		assertThat(rules.findByEnabledTrueAndTriggerTypeOrderByIdAsc(RuleTrigger.CRON)).isEmpty();
	}

	private boolean tryInsert(Long rule, String runKey, Instant ranAt) {
		Boolean saved = tx.execute(status -> runs.tryInsert(rule, runKey, ranAt));
		return Boolean.TRUE.equals(saved);
	}

	@Test
	void tryInsertSaysYesOnceThenNo() {
		Long rule = rules.findByCode("FEE_REMINDER_10TH").orElseThrow().getId();

		assertThat(tryInsert(rule, "2026-10-10", TEN_OCT)).isTrue();
		// The scheduler fires again after a restart at 10:00:30: same key, nothing new.
		assertThat(tryInsert(rule, "2026-10-10", TEN_OCT.plusSeconds(30))).isFalse();
		// Next month is a new run.
		assertThat(tryInsert(rule, "2026-11-10", TEN_OCT)).isTrue();

		assertThat(jdbc.queryForObject("select count(*) from notification_rule_run where rule_id = ?", Integer.class,
				rule))
			.isEqualTo(2);
		assertThat(runs.findFirstByIdRuleIdOrderByRanAtDesc(rule)).isPresent();
	}

	@Test
	void aRunIsLinkedToItsNotification() {
		Long rule = rules.findByCode("BIRTHDAY").orElseThrow().getId();
		long template = jdbc.queryForObject("insert into message_template (kind, name, channel, body, approval) "
				+ "values ('GREETING', 'Birthday', 'WHATSAPP', 'जन्मदिन मुबारक', 'APPROVED') returning id", Long.class);
		long notification = jdbc.queryForObject("insert into notification (kind, name, template_id, body, audience, "
				+ "source, rule_id, created_by) values ('GREETING', 'Birthday wish', ?, 'x', '{}', 'RULE', ?, ?) "
				+ "returning id", Long.class, template, rule, anyUserId());

		tx.executeWithoutResult(status -> {
			assertThat(runs.tryInsert(rule, "2026:student:129", TEN_OCT)).isTrue();
			assertThat(runs.linkNotification(rule, "2026:student:129", notification)).isEqualTo(1);
		});

		assertThat(jdbc.queryForObject("select notification_id from notification_rule_run where run_key = ?",
				Long.class, "2026:student:129"))
			.isEqualTo(notification);
	}

}

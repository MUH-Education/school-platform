package com.muhjain.school.notification;

import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.service.notification.RuleScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code RuleScheduler} (task 12.4) on the small school of {@link NotificationTestBase}. Times in the tests are
 * school time (Asia/Kolkata). The scheduler job is off in tests; the test calls {@code runOnce} with the time it wants.
 */
class RuleSchedulerTest extends NotificationTestBase {

	private static final ZoneId SCHOOL = ZoneId.of("Asia/Kolkata");

	@Autowired
	private RuleScheduler scheduler;

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

	private void turnOn(String code, long templateId) {
		jdbc.update("update notification_rule set template_id = ?, enabled = true, updated_by = ? where code = ?",
				templateId, owner.getId(), code);
	}

	private void feeReminderOn() {
		turnOn("FEE_REMINDER_10TH", template("FEE_REMINDER", "{Amount due} बाकी है।", "APPROVED"));
	}

	/** Moves the app clock to this school time and runs the scheduler once, like the job would. */
	private int runAt(String schoolTime) {
		Instant now = LocalDateTime.parse(schoolTime).atZone(SCHOOL).toInstant();
		clock.setInstant(now);
		return scheduler.runOnce(now);
	}

	private int notificationsOf(String code) {
		return jdbc.queryForObject("select count(*) from notification n join notification_rule r on r.id = n.rule_id "
				+ "where r.code = ?", Integer.class, code);
	}

	@Test
	void cronRuleFiresOnceEvenIfSchedulerRunsTwice() {
		feeReminderOn();

		assertThat(runAt("2026-10-10T09:59:05")).isZero();      // not yet
		assertThat(runAt("2026-10-10T10:00:05")).isEqualTo(1);
		// The app restarts and the scheduler runs again in the same window: same key, nothing new.
		assertThat(runAt("2026-10-10T10:00:30")).isZero();
		assertThat(runAt("2026-10-10T10:01:05")).isZero();

		assertThat(notificationsOf("FEE_REMINDER_10TH")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select run_key from notification_rule_run", String.class)).isEqualTo("2026-10-10");
	}

	@Test
	void cronRuleFiresAfterShortDowntimeButNotAfterLong() {
		feeReminderOn();

		// The server was down at 10:00 and is back at 10:01: still fired.
		assertThat(runAt("2026-10-10T10:01:00")).isEqualTo(1);
		// Next month it is back only at 10:05: too late, nothing (the owner can press "Run now").
		assertThat(runAt("2026-11-10T10:05:00")).isZero();
		assertThat(notificationsOf("FEE_REMINDER_10TH")).isEqualTo(1);
	}

	@Test
	void birthdayRuleWishesEachChildOncePerYear() {
		turnOn("BIRTHDAY", template("GREETING", "{Child name} को जन्मदिन मुबारक!", "APPROVED"));
		jdbc.update("update student set dob = date '2015-10-23' where id = ?", siya);

		assertThat(runAt("2026-10-23T06:00:05")).isEqualTo(1);
		assertThat(runAt("2026-10-23T06:01:00")).isZero();
		assertThat(jdbc.queryForObject("select audience -> 'groups' ->> 0 from notification", String.class))
			.isEqualTo("BIRTHDAY_TODAY");
		// The next day nobody has a birthday: the run is saved, no notification.
		assertThat(runAt("2026-10-24T06:00:05")).isZero();
		// A year later, Siya again.
		assertThat(runAt("2027-10-23T06:00:05")).isEqualTo(1);

		assertThat(notificationsOf("BIRTHDAY")).isEqualTo(2);
	}

	@Test
	void festivalFiresOnlyOnItsDatesWithTheDaysName() {
		turnOn("FESTIVAL", template("GREETING", "{Parent name} जी, {Festival} की शुभकामनाएँ!", "APPROVED"));
		jdbc.update("update notification_rule set fixed_values = "
				+ "'{\"dates\": [\"2026-11-08\"], \"Festival\": {\"2026-11-08\": \"दीपावली\"}}' where code = 'FESTIVAL'");

		assertThat(runAt("2026-11-07T09:00:05")).isZero();
		assertThat(runAt("2026-11-08T09:00:05")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select fixed_values ->> 'Festival' from notification", String.class))
			.isEqualTo("दीपावली");
	}

	@Test
	void disabledRuleNeverFires() {
		turnOn("FEE_REMINDER_10TH", template("FEE_REMINDER", "{Amount due} बाकी है।", "APPROVED"));
		jdbc.update("update notification_rule set enabled = false where code = 'FEE_REMINDER_10TH'");

		assertThat(runAt("2026-10-10T10:00:05")).isZero();
		assertThat(jdbc.queryForObject("select count(*) from notification_rule_run", Integer.class)).isZero();
	}

	@Test
	void aBrokenRuleDoesNotStopTheOthers() {
		// Both reminders at 10:00 on the 10th. The first has a template WhatsApp has not approved yet.
		turnOn("FEE_REMINDER_10TH", template("FEE_REMINDER", "{Amount due} बाकी है।", "WAITING"));
		turnOn("FEE_REMINDER_20TH", template("FEE_REMINDER", "{Amount due} अब भी बाकी है।", "APPROVED"));
		jdbc.update("update notification_rule set cron = '0 0 10 10 * *' where code = 'FEE_REMINDER_20TH'");
		// And one rule with a cron nobody can read.
		turnOn("BIRTHDAY", template("GREETING", "जन्मदिन मुबारक!", "APPROVED"));
		jdbc.update("update notification_rule set cron = 'every day' where code = 'BIRTHDAY'");

		assertThat(runAt("2026-10-10T10:00:05")).isEqualTo(1);
		assertThat(notificationsOf("FEE_REMINDER_20TH")).isEqualTo(1);
		assertThat(notificationsOf("FEE_REMINDER_10TH")).isZero();
		// The broken one left no run behind, so it can still run once it is fixed.
		assertThat(jdbc.queryForObject("select count(*) from notification_rule_run", Integer.class)).isEqualTo(1);
	}

}

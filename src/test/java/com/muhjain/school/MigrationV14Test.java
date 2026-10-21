package com.muhjain.school;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The database rules of V14__notifications.sql. They hold even if the Java code has a bug. */
class MigrationV14Test extends AbstractIntegrationTest {

	private static final String PHONE = "+919811100001";

	private long addTemplate(String kind) {
		return jdbc.queryForObject("insert into message_template (kind, name, channel, body, approval) "
				+ "values (?, 'Open day', 'WHATSAPP', 'नमस्ते {Parent name}', 'APPROVED') returning id", Long.class, kind);
	}

	private long addNotification(String kind) {
		return jdbc.queryForObject("insert into notification (kind, name, template_id, body, audience, created_by) "
				+ "values (?, 'Open day, Sunday 25 October', ?, 'नमस्ते {Parent name}', "
				+ "'{\"groups\":[\"CURRENT_PARENTS\"],\"filter\":{}}', ?) returning id", Long.class, kind,
				addTemplate(kind), anyUserId());
	}

	private long addGuardian(String phone) {
		return jdbc.queryForObject("insert into guardian (name, phone) values ('Rajesh Punia', ?) returning id",
				Long.class, phone);
	}

	private void addOutboxRow(long notificationId, String purpose, String channel, String phone) {
		jdbc.update("insert into message_outbox (purpose, channel, phone, notification_id, body) "
				+ "values (?, ?, ?, ?, 'Open day on Sunday')", purpose, channel, phone, notificationId);
	}

	private static void refused(Runnable sql) {
		assertThatThrownBy(sql::run).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void migrationV14CreatesEverything() {
		for (String table : new String[] { "notification", "message_template_button", "message_stop",
				"inbound_message" }) {
			assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name = ?",
					Integer.class, table))
				.as(table)
				.isEqualTo(1);
		}
		for (String column : new String[] { "notification_id", "staff_id", "enquiry_id", "read_at", "button_action",
				"media_key", "template_values", "skip_reason" }) {
			assertThat(jdbc.queryForObject("select count(*) from information_schema.columns "
					+ "where table_name = 'message_outbox' and column_name = ?", Integer.class, column))
				.as(column)
				.isEqualTo(1);
		}
		assertThat(jdbc.queryForObject("select character_maximum_length from information_schema.columns "
				+ "where table_name = 'message_outbox' and column_name = 'body'", Integer.class))
			.isEqualTo(1024);
	}

	@Test
	void existingTemplatesAreSystemAndKeepTheirCodes() {
		assertThat(jdbc.queryForObject("select count(*) from message_template", Integer.class)).isPositive();
		assertThat(jdbc.queryForObject("select count(*) from message_template where kind <> 'SYSTEM' or code is null",
				Integer.class))
			.isZero();
		// Still one row per (code, gender) among the system texts.
		refused(() -> jdbc.update("insert into message_template (kind, code, gender, name, channel, body) "
				+ "values ('SYSTEM', 'BOARDED_MORNING', 'M', 'Copy', 'SMS', 'x')"));
		// A system text needs its code.
		refused(() -> jdbc.update("insert into message_template (kind, name, channel, body) "
				+ "values ('SYSTEM', 'No code', 'SMS', 'x')"));
	}

	@Test
	void notificationTemplatesNeedAKindButNoCode() {
		// Two notification templates, no code at all: fine (nulls of SYSTEM-only unique do not matter).
		addTemplate("NOTICE");
		addTemplate("NOTICE");
		// The kind has no default: a new row must say what it is.
		refused(() -> jdbc.update("insert into message_template (name, channel, body) values ('X', 'WHATSAPP', 'x')"));
		refused(() -> addTemplate("COST"));
	}

	@Test
	void templateHeaderAndRejectionRules() {
		long id = addTemplate("MARKETING");
		refused(() -> jdbc.update("update message_template set header_type = 'IMAGE' where id = ?", id));
		jdbc.update("update message_template set header_type = 'IMAGE', media_key = 'notifications/a.jpg' where id = ?",
				id);
		refused(() -> jdbc.update("update message_template set header_type = 'GIF' where id = ?", id));
		// A reason only for a REJECTED template.
		refused(() -> jdbc.update("update message_template set rejection_reason = 'Too promotional' where id = ?", id));
		jdbc.update("update message_template set approval = 'REJECTED', rejection_reason = 'Too promotional' "
				+ "where id = ?", id);
		assertThat(jdbc.queryForObject("select provider_language from message_template where id = ?", String.class, id))
			.isEqualTo("hi");
	}

	@Test
	void buttonsFollowWhatsAppRules() {
		long id = addTemplate("MARKETING");
		String sql = "insert into message_template_button (template_id, seq_no, type, text, action) values (?, ?, ?, ?, ?)";
		jdbc.update(sql, id, 1, "CALL", "Call school", "+919800000100");
		jdbc.update(sql, id, 2, "URL", "See photos", "https://muhjain.school/open-day");
		jdbc.update(sql, id, 3, "REPLY", "Stop messages", "STOP_MESSAGES");

		refused(() -> jdbc.update(sql, id, 3, "REPLY", "Got it", "GOT_IT"));               // seq 3 is taken
		refused(() -> jdbc.update(sql, id, 4, "REPLY", "Got it", "GOT_IT"));               // 3 buttons at most
		long other = addTemplate("NOTICE");
		refused(() -> jdbc.update(sql, other, 1, "REPLY", "Buy now", "BUY_NOW"));          // unknown action
		refused(() -> jdbc.update(sql, other, 1, "CALL", "Call", "12345"));                 // not a phone
		refused(() -> jdbc.update(sql, other, 1, "URL", "Site", "http://muhjain.school"));  // only https
		refused(() -> jdbc.update(sql, other, 1, "REPLY", "This text is much too long", "GOT_IT")); // 25 letters
		refused(() -> jdbc.update(sql, other, 1, "LINK", "Site", "https://muhjain.school"));
	}

	@Test
	void notificationChecks() {
		long id = addNotification("MARKETING");
		assertThat(jdbc.queryForMap("select status, source, fixed_values::text as fv from notification where id = ?", id))
			.containsEntry("status", "DRAFT")
			.containsEntry("source", "MANUAL")
			.containsEntry("fv", "{}");

		long template = addTemplate("NOTICE");
		refused(() -> jdbc.update("insert into notification (kind, name, template_id, body, audience, created_by) "
				+ "values ('COST', 'x', ?, 'x', '{}', ?)", template, anyUserId()));
		refused(() -> jdbc.update("update notification set status = 'PAUSED' where id = ?", id));
		// SCHEDULED needs a time.
		refused(() -> jdbc.update("update notification set status = 'SCHEDULED' where id = ?", id));
		jdbc.update("update notification set status = 'SCHEDULED', scheduled_at = now() + interval '1 day' where id = ?",
				id);
		// A file needs its type, and a type needs a file.
		refused(() -> jdbc.update("update notification set media_key = 'notifications/a.jpg' where id = ?", id));
		refused(() -> jdbc.update("update notification set media_type = 'IMAGE' where id = ?", id));
		jdbc.update("update notification set media_key = 'notifications/a.jpg', media_type = 'IMAGE' where id = ?", id);
		// rule_id only for a notification made by a rule (Phase 12).
		refused(() -> jdbc.update("update notification set rule_id = 1 where id = ?", id));
		jdbc.update("update notification set source = 'RULE', rule_id = 1 where id = ?", id);
	}

	@Test
	void outboxTakesNotificationRowsAndSkippedNeedsAReason() {
		long id = addNotification("NOTICE");
		addOutboxRow(id, "NOTICE", "WHATSAPP", PHONE);
		refused(() -> jdbc.update("insert into message_outbox (purpose, channel, phone, body) "
				+ "values ('COST', 'SMS', '+919811100002', 'x')"));

		String skip = "insert into message_outbox (purpose, channel, phone, notification_id, body, status, skip_reason) "
				+ "values ('NOTICE', 'WHATSAPP', ?, ?, 'x', ?, ?)";
		jdbc.update(skip, "+919811100002", id, "SKIPPED", "ALREADY_PAID");
		refused(() -> jdbc.update(skip, "+919811100003", id, "SKIPPED", null));
		refused(() -> jdbc.update(skip, "+919811100004", id, "QUEUED", "ALREADY_PAID"));

		assertThat(jdbc.queryForObject("select template_values::text from message_outbox where phone = ?", String.class,
				PHONE))
			.isEqualTo("{}");
	}

	@Test
	void onePhoneGetsOneCopyPerChannel() {
		long id = addNotification("URGENT");
		addOutboxRow(id, "URGENT", "WHATSAPP", PHONE);
		// URGENT goes on both channels to the same phone.
		addOutboxRow(id, "URGENT", "SMS", PHONE);
		refused(() -> addOutboxRow(id, "URGENT", "WHATSAPP", PHONE));
		// The test copy to the sender and an SMS fallback do not count.
		addOutboxRow(id, "TEST", "WHATSAPP", PHONE);
		long whatsApp = jdbc.queryForObject("select id from message_outbox where channel = 'WHATSAPP' "
				+ "and purpose = 'URGENT'", Long.class);
		jdbc.update("insert into message_outbox (purpose, channel, phone, notification_id, body, fallback_of_id) "
				+ "values ('URGENT', 'SMS', ?, ?, 'x', ?)", PHONE, id, whatsApp);
		// Another notification may reach the same phone.
		addOutboxRow(addNotification("URGENT"), "URGENT", "WHATSAPP", PHONE);
	}

	@Test
	void aFamilyIsOnTheStopListOnce() {
		long guardian = addGuardian(PHONE);
		jdbc.update("insert into message_stop (guardian_id, reason) values (?, 'WROTE_STOP')", guardian);
		refused(() -> jdbc.update("insert into message_stop (guardian_id, reason) values (?, 'TAPPED_BUTTON')",
				guardian));
		long other = addGuardian("+919811100002");
		refused(() -> jdbc.update("insert into message_stop (guardian_id, reason) values (?, 'ANGRY')", other));
	}

	@Test
	void inboundMessageIsSavedOncePerProviderRef() {
		String sql = "insert into inbound_message (phone, text, button_action, provider_ref, received_at) "
				+ "values (?, ?, ?, ?, now())";
		jdbc.update(sql, PHONE, "STOP", null, "wamid.1");
		refused(() -> jdbc.update(sql, PHONE, "STOP", null, "wamid.1"));
		// A button tap has no text; a reply from abroad is still saved.
		jdbc.update(sql, PHONE, null, "STOP_MESSAGES", "wamid.2");
		jdbc.update(sql, "+447700900123", "Hello", null, "wamid.3");
		refused(() -> jdbc.update(sql, "98111 00001", "Hello", null, "wamid.4"));
		refused(() -> jdbc.update(sql, PHONE, null, null, "wamid.5"));
		refused(() -> jdbc.update(sql, PHONE, null, "BUY_NOW", "wamid.6"));
		assertThat(jdbc.queryForObject("select count(*) from inbound_message where not handled", Integer.class))
			.isEqualTo(3);
	}

	@Test
	void quietHoursAndWeeklyCapAreSettings() {
		assertThat(jdbc.queryForList("select key || '=' || value from app_setting where key like 'notifications.%' "
				+ "order by key", String.class))
			.containsExactly("notifications.max_marketing_per_family_per_week=2", "notifications.quiet_from=21:00",
					"notifications.quiet_to=08:00");
	}

	@Test
	void noMoneyColumns() {
		assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name in "
				+ "('notification', 'message_template', 'message_template_button', 'message_outbox', 'message_stop', "
				+ "'inbound_message') and (column_name like '%cost%' or column_name like '%price%' "
				+ "or column_name like '%paise%' or column_name like '%budget%')", Integer.class))
			.isZero();
	}

}

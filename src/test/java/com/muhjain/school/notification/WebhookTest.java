package com.muhjain.school.notification;

import com.muhjain.school.model.dto.request.NotificationRequest;
import com.muhjain.school.model.dto.request.SendNotificationRequest;
import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.model.enums.TemplateKind;
import com.muhjain.school.service.messaging.WebhookSignature;
import com.muhjain.school.service.notification.NotificationDispatcher;
import com.muhjain.school.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 11.13: the provider's callbacks, signed with the test secret as the provider would. On the small school of
 * {@link NotificationTestBase}: Punia +919811100001, Chauhan +919811100002 (already stopped), Sharma +919811100004,
 * the open enquiry Suresh +919811100005.
 */
class WebhookTest extends NotificationTestBase {

	private static final String SECRET = "test-only-webhook-secret-not-for-production-01";

	private static final String URL = "/api/v1/webhooks/log";

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationDispatcher dispatcher;

	private AppUser owner;

	@BeforeEach
	void owner() {
		owner = addUser("+919812340001", Role.OWNER);
	}

	private ResultActions signed(String path, String json) throws Exception {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		return mockMvc.perform(post(URL + path).contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.header("X-Signature", WebhookSignature.sign(SECRET, body)));
	}

	private static String inbound(String ref, String phone, String text, String button, String inReplyTo) {
		return "{\"providerRef\":\"" + ref + "\",\"phone\":\"" + phone + "\""
				+ ((text == null) ? "" : ",\"text\":\"" + text + "\"")
				+ ((button == null) ? "" : ",\"buttonAction\":\"" + button + "\"")
				+ ((inReplyTo == null) ? "" : ",\"inReplyTo\":\"" + inReplyTo + "\"") + "}";
	}

	private static String statusBody(String ref, String status, String problem) {
		return "{\"providerRef\":\"" + ref + "\",\"status\":\"" + status + "\",\"at\":\"2026-10-07T10:05:00+05:30\""
				+ ((problem == null) ? "" : ",\"problem\":\"" + problem + "\"") + "}";
	}

	/** A message we sent, as the provider knows it. */
	private long sentRow(String ref, String channel, Long notificationId, long guardianId, String phone) {
		return jdbc.queryForObject("insert into message_outbox (purpose, channel, phone, body, status, provider_ref, "
				+ "notification_id, guardian_id, sent_at, next_try_at, template_values) values ('NOTICE', ?, ?, 'x', "
				+ "'SENT', ?, ?, ?, ?, ?, '{\"Child name\": \"Dev\"}'::jsonb) returning id", Long.class, channel, phone,
				ref, notificationId, guardianId, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
	}

	private long notification(NotificationKind kind, String body, String smsBody) {
		long templateId = jdbc.queryForObject("insert into message_template (kind, name, channel, body, approved_body, "
				+ "sms_body, approval) values (?, 'T', 'WHATSAPP', ?, ?, ?, 'APPROVED') returning id", Long.class,
				TemplateKind.of(kind).name(), body, body, smsBody);
		return notificationService.create(new NotificationRequest("Test", kind, templateId, null, null, Map.of(), null,
				new Audience(List.of(AudienceGroup.CURRENT_PARENTS), null)), Role.OWNER.permissions(), owner.getId())
			.id();
	}

	private int stopRows(long guardianId) {
		return jdbc.queryForObject("select count(*) from message_stop where guardian_id = ?", Integer.class, guardianId);
	}

	// ---------------------------------------------------------------- the signature

	@Test
	void wrongOrMissingSignatureIs401AndSavesNothing() throws Exception {
		String json = inbound("wamid.IN1", "+919811100004", "STOP", null, null);
		mockMvc.perform(post(URL + "/inbound").contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("BAD_SIGNATURE"));
		mockMvc.perform(post(URL + "/inbound").contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.header("X-Signature", WebhookSignature.sign("another-secret", json.getBytes(StandardCharsets.UTF_8))))
			.andExpect(status().isUnauthorized());
		// A signature of another body does not fit this one.
		mockMvc.perform(post(URL + "/inbound").contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.header("X-Signature", WebhookSignature.sign(SECRET, "{}".getBytes(StandardCharsets.UTF_8))))
			.andExpect(status().isUnauthorized());
		assertThat(jdbc.queryForObject("select count(*) from inbound_message", Integer.class)).isZero();
		assertThat(stopRows(sharma)).isZero();
	}

	@Test
	void aGoodSignatureMayHaveTheSha256Prefix() throws Exception {
		byte[] body = inbound("wamid.IN2", "919811100004", "Thank you", null, null).getBytes(StandardCharsets.UTF_8);
		mockMvc.perform(post(URL + "/inbound").contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.header("X-Signature", "sha256=" + WebhookSignature.sign(SECRET, body).toUpperCase()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.changed").value(true));
		// "919811100004" from the provider is the same phone as ours.
		assertThat(jdbc.queryForMap("select phone, guardian_id, handled from inbound_message"))
			.containsEntry("phone", "+919811100004")
			.containsEntry("guardian_id", sharma)
			.containsEntry("handled", false);
	}

	@Test
	void badBodyOrProvider() throws Exception {
		signed("/status", "not json").andExpect(status().isBadRequest());
		signed("/status", "{\"providerRef\":\"x\",\"status\":\"OPENED\"}").andExpect(status().isBadRequest());
		signed("/inbound", "{\"providerRef\":\"x\",\"phone\":\"+919811100004\"}").andExpect(status().isBadRequest());
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		mockMvc.perform(post("/api/v1/webhooks/BAD_NAME/status").contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.header("X-Signature", WebhookSignature.sign(SECRET, body))).andExpect(status().isNotFound());
	}

	// ---------------------------------------------------------------- replies and STOP

	@Test
	void inboundStopAddsStopRowOnce() throws Exception {
		signed("/inbound", inbound("wamid.IN3", "+919811100004", " Stop. ", null, null)).andExpect(status().isOk())
			.andExpect(jsonPath("$.changed").value(true));
		assertThat(stopRows(sharma)).isEqualTo(1);
		assertThat(jdbc.queryForMap("select reason from message_stop where guardian_id = ?", sharma))
			.containsEntry("reason", "WROTE_STOP");
		assertThat(jdbc.queryForMap("select handled, note from inbound_message where provider_ref = 'wamid.IN3'"))
			.containsEntry("handled", true)
			.containsEntry("note", "Put on the stop list.");
		// The same callback again: nothing new.
		signed("/inbound", inbound("wamid.IN3", "+919811100004", " Stop. ", null, null))
			.andExpect(jsonPath("$.changed").value(false));
		// Writing STOP again: still one stop row.
		signed("/inbound", inbound("wamid.IN4", "+919811100004", "STOP", null, null));
		assertThat(stopRows(sharma)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from inbound_message", Integer.class)).isEqualTo(2);
		// The change history has it, with a masked phone.
		assertThat(jdbc.queryForObject("select summary from audit_log where entity_type = 'MESSAGE_STOP'", String.class))
			.startsWith("+91XXXXXX0004 is on the stop list (wrote STOP)");
	}

	@Test
	void hindiStopWorks() throws Exception {
		signed("/inbound", inbound("wamid.IN5", "+919811100001", "बंद।", null, null)).andExpect(status().isOk());
		assertThat(stopRows(punia)).isEqualTo(1);
	}

	@Test
	void buttonTapIsSavedOnTheRow() throws Exception {
		long n = notification(NotificationKind.MARKETING, "Open day", null);
		long row = sentRow("wamid.OUT1", "WHATSAPP", n, sharma, "+919811100004");
		signed("/inbound", inbound("wamid.IN6", "+919811100004", null, "STOP_MESSAGES", "wamid.OUT1"))
			.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select button_action from message_outbox where id = ?", String.class, row))
			.isEqualTo("STOP_MESSAGES");
		assertThat(jdbc.queryForMap("select reason, notification_id from message_stop where guardian_id = ?", sharma))
			.containsEntry("reason", "TAPPED_BUTTON")
			.containsEntry("notification_id", n);
		assertThat(jdbc.queryForObject("select in_reply_to_outbox_id from inbound_message", Long.class)).isEqualTo(row);
	}

	@Test
	void stopFromAPhoneThatIsNotAParentChangesNothing() throws Exception {
		signed("/inbound", inbound("wamid.IN7", "+919811100005", "stop", null, null)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from message_stop", Integer.class)).isEqualTo(1);   // Chauhan only
		assertThat(jdbc.queryForMap("select handled, note, enquiry_id from inbound_message"))
			.containsEntry("handled", false)
			.containsEntry("note", "Wrote STOP, but this phone is not a parent here: nothing changed.");
		assertThat(jdbc.queryForMap("select enquiry_id from inbound_message").get("enquiry_id")).isNotNull();
	}

	@Test
	void otherButtonsWaitForAPerson() throws Exception {
		signed("/inbound", inbound("wamid.IN8", "+919811100001", null, "BOOK_VISIT", null));
		signed("/inbound", inbound("wamid.IN9", "+919811100004", null, "GOT_IT", null));
		assertThat(jdbc.queryForMap("select handled, note from inbound_message where provider_ref = 'wamid.IN8'"))
			.containsEntry("handled", false)
			.containsEntry("note", "Wants to book a school visit. Call back and add an enquiry.");
		assertThat(jdbc.queryForMap("select handled from inbound_message where provider_ref = 'wamid.IN9'"))
			.containsEntry("handled", true);
		assertThat(jdbc.queryForObject("select count(*) from enquiry", Integer.class)).isEqualTo(2);   // none made
	}

	@Test
	void afterStopTheNextMarketingSkipsTheFamilyButANoticeStillGoes() throws Exception {
		signed("/inbound", inbound("wamid.IN10", "+919811100004", "STOP", null, null));
		long marketing = notification(NotificationKind.MARKETING, "Open day on Sunday", null);
		long notice = notification(NotificationKind.NOTICE, "PTM on Saturday", null);
		notificationService.send(marketing, new SendNotificationRequest(true, null), Role.OWNER.permissions(),
				owner.getId());
		notificationService.send(notice, new SendNotificationRequest(true, null), Role.OWNER.permissions(),
				owner.getId());
		dispatcher.runOnce();
		assertThat(jdbc.queryForMap("select status, skip_reason from message_outbox where notification_id = ? "
				+ "and phone = '+919811100004'", marketing)).containsEntry("status", "SKIPPED")
			.containsEntry("skip_reason", "STOPPED");
		assertThat(jdbc.queryForMap("select status from message_outbox where notification_id = ? "
				+ "and phone = '+919811100004'", notice)).containsEntry("status", "QUEUED");
	}

	// ---------------------------------------------------------------- delivered / read / failed

	@Test
	void statusWebhookIsIdempotent() throws Exception {
		long row = sentRow("wamid.OUT2", "WHATSAPP", null, sharma, "+919811100004");
		signed("/status", statusBody("wamid.OUT2", "DELIVERED", null)).andExpect(jsonPath("$.changed").value(true));
		Map<String, Object> once = jdbc.queryForMap("select status, delivered_at from message_outbox where id = ?", row);
		assertThat(once.get("status")).isEqualTo("DELIVERED");
		assertThat(((Timestamp) once.get("delivered_at")).toInstant()).isEqualTo(java.time.Instant.parse(
				"2026-10-07T04:35:00Z"));
		signed("/status", statusBody("wamid.OUT2", "DELIVERED", null)).andExpect(jsonPath("$.changed").value(false));
		assertThat(jdbc.queryForMap("select status, delivered_at from message_outbox where id = ?", row)).isEqualTo(once);
	}

	@Test
	void readNeverGoesBackToDelivered() throws Exception {
		long row = sentRow("wamid.OUT3", "WHATSAPP", null, sharma, "+919811100004");
		signed("/status", statusBody("wamid.OUT3", "READ", null)).andExpect(jsonPath("$.changed").value(true));
		signed("/status", statusBody("wamid.OUT3", "DELIVERED", null)).andExpect(jsonPath("$.changed").value(false));
		signed("/status", statusBody("wamid.OUT3", "FAILED", "PHONE_OFF")).andExpect(jsonPath("$.changed").value(false));
		Map<String, Object> r = jdbc.queryForMap("select status, read_at, delivered_at from message_outbox where id = ?",
				row);
		assertThat(r.get("status")).isEqualTo("DELIVERED");
		assertThat(r.get("read_at")).isNotNull();
		assertThat(r.get("delivered_at")).isNotNull();   // read means delivered
	}

	@Test
	void notOnWhatsAppStatusMakesTheSmsCopy() throws Exception {
		long n = notification(NotificationKind.NOTICE, "PTM on Saturday", "{Child name}: PTM शनिवार।");
		long row = sentRow("wamid.OUT4", "WHATSAPP", n, sharma, "+919811100004");
		signed("/status", statusBody("wamid.OUT4", "FAILED", "not_on_whatsapp")).andExpect(jsonPath("$.changed").value(true));
		assertThat(jdbc.queryForMap("select status, problem from message_outbox where id = ?", row))
			.containsEntry("status", "FAILED")
			.containsEntry("problem", "NOT_ON_WHATSAPP");
		signed("/status", statusBody("wamid.OUT4", "FAILED", "NOT_ON_WHATSAPP"));
		List<Map<String, Object>> sms = jdbc.queryForList("select body, status from message_outbox where fallback_of_id = ?",
				row);
		assertThat(sms).hasSize(1);
		assertThat(sms.getFirst()).containsEntry("body", "Dev: PTM शनिवार।").containsEntry("status", "QUEUED");
	}

	@Test
	void unknownProviderRefIsIgnored() throws Exception {
		signed("/status", statusBody("wamid.NOBODY", "READ", null)).andExpect(status().isOk())
			.andExpect(jsonPath("$.received").value(true))
			.andExpect(jsonPath("$.changed").value(false));
	}

	// ---------------------------------------------------------------- template approval

	@Test
	void templateStatusApprovesOrRejects() throws Exception {
		long id = jdbc.queryForObject("insert into message_template (kind, name, channel, body, approval, "
				+ "provider_template_name) values ('MARKETING', 'Open day', 'WHATSAPP', 'Open day {Parent name}', 'WAITING', "
				+ "'open_day_oct') returning id", Long.class);
		signed("/template-status", "{\"templateName\":\"open_day_oct\",\"status\":\"REJECTED\",\"reason\":\"Too promotional\"}")
			.andExpect(jsonPath("$.changed").value(true));
		assertThat(jdbc.queryForMap("select approval, rejection_reason from message_template where id = ?", id))
			.containsEntry("approval", "REJECTED")
			.containsEntry("rejection_reason", "Too promotional");
		signed("/template-status", "{\"templateName\":\"open_day_oct\",\"status\":\"APPROVED\"}")
			.andExpect(jsonPath("$.changed").value(true));
		signed("/template-status", "{\"templateName\":\"open_day_oct\",\"status\":\"APPROVED\"}")
			.andExpect(jsonPath("$.changed").value(false));
		assertThat(jdbc.queryForMap("select approval, approved_body, rejection_reason from message_template where id = ?",
				id)).containsEntry("approval", "APPROVED")
			.containsEntry("approved_body", "Open day {Parent name}")
			.containsEntry("rejection_reason", null);
		signed("/template-status", "{\"templateName\":\"not_ours\",\"status\":\"APPROVED\"}")
			.andExpect(jsonPath("$.changed").value(false));
	}

}

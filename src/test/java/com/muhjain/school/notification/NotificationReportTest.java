package com.muhjain.school.notification;

import com.muhjain.school.model.dto.request.NotificationRequest;
import com.muhjain.school.model.dto.request.SendNotificationRequest;
import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.model.enums.TemplateKind;
import com.muhjain.school.service.messaging.OutboxWorker;
import com.muhjain.school.service.notification.NotificationDispatcher;
import com.muhjain.school.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 11.14: the report and "resend failed", on the small school of {@link NotificationTestBase}: Punia
 * +919811100001, Chauhan +919811100002 (stop list), Sharma +919811100004. 7 Oct 2026, 10 am.
 */
class NotificationReportTest extends NotificationTestBase {

	private static final String URL = "/api/v1/notifications/";

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationDispatcher dispatcher;

	@Autowired
	private OutboxWorker worker;

	private AppUser owner;

	private String token;

	@BeforeEach
	void owner() {
		owner = addUser("+919812340001", Role.OWNER);
		token = tokenFor(owner);
	}

	private long sent(NotificationKind kind) {
		long templateId = jdbc.queryForObject("insert into message_template (kind, name, channel, body, approved_body, "
				+ "approval) values (?, 'T', 'WHATSAPP', 'Hello {Parent name}', 'Hello {Parent name}', 'APPROVED') "
				+ "returning id", Long.class, TemplateKind.of(kind).name());
		long id = notificationService.create(new NotificationRequest("Open day", kind, templateId, null, null, Map.of(),
				null, new Audience(List.of(AudienceGroup.CURRENT_PARENTS), null)), Role.OWNER.permissions(),
				owner.getId()).id();
		notificationService.send(id, new SendNotificationRequest(true, null), Role.OWNER.permissions(), owner.getId());
		dispatcher.runOnce();
		worker.runOnce();
		return id;
	}

	private ResultActions report(long id) throws Exception {
		return mockMvc.perform(get(URL + id + "/report").header("Authorization", bearer(token)))
			.andExpect(status().isOk());
	}

	private void set(long id, String phone, String sql, Object... args) {
		Object[] all = new Object[args.length + 2];
		System.arraycopy(args, 0, all, 0, args.length);
		all[args.length] = id;
		all[args.length + 1] = phone;
		jdbc.update("update message_outbox set " + sql + " where notification_id = ? and phone = ?", all);
	}

	private static Timestamp at(String instant) {
		return Timestamp.from(Instant.parse(instant));
	}

	@Test
	void reportCountsEqualOutboxRows() throws Exception {
		long id = sent(NotificationKind.MARKETING);
		// Punia read it at 10:12 and tapped "Book a visit"; Sharma's failed: phone off.
		set(id, "+919811100001", "status = 'DELIVERED', delivered_at = ?, read_at = ?, button_action = 'BOOK_VISIT'",
				at("2026-10-07T04:40:00Z"), at("2026-10-07T04:42:00Z"));
		set(id, "+919811100004", "status = 'FAILED', problem = 'PHONE_OFF', attempts = 3");
		// The test copy to the owner (sent while it was a draft) is not one of the counts.
		jdbc.update("insert into message_outbox (purpose, channel, phone, body, status, notification_id) "
				+ "values ('TEST', 'WHATSAPP', '+919812340001', 'x', 'TEST_ONLY', ?)", id);

		report(id).andExpect(jsonPath("$.recipientCount").value(2))
			.andExpect(jsonPath("$.rows").value(3))
			.andExpect(jsonPath("$.gotIt").value(1))
			.andExpect(jsonPath("$.delivered").value(1))
			.andExpect(jsonPath("$.read").value(1))
			.andExpect(jsonPath("$.skipped").value(1))
			.andExpect(jsonPath("$.failed").value(1))
			.andExpect(jsonPath("$.byStatus.DELIVERED").value(1))
			.andExpect(jsonPath("$.byStatus.SKIPPED").value(1))
			.andExpect(jsonPath("$.byStatus.FAILED").value(1))
			.andExpect(jsonPath("$.byChannel.WHATSAPP").value(3))
			.andExpect(jsonPath("$.byProblem.PHONE_OFF").value(1))
			.andExpect(jsonPath("$.byButton.BOOK_VISIT").value(1))
			.andExpect(jsonPath("$.readByHour", hasSize(1)))
			.andExpect(jsonPath("$.readByHour[0].hour").value("2026-10-07T10:00:00+05:30"))
			.andExpect(jsonPath("$.readByHour[0].reads").value(1))
			.andExpect(jsonPath("$.testCopies").value(1));

		// Exactly the database: the status counts plus the test copy = every row of the notification.
		List<Map<String, Object>> db = jdbc.queryForList("select status, count(*) as n from message_outbox "
				+ "where notification_id = ? and purpose <> 'TEST' group by status", id);
		assertThat(db).hasSize(3).allSatisfy(r -> assertThat(((Number) r.get("n")).intValue()).isEqualTo(1));
		assertThat(jdbc.queryForObject("select count(*) from message_outbox where notification_id = ?", Integer.class,
				id)).isEqualTo(4);
	}

	@Test
	void skippedRowsAppearInReportWithReason() throws Exception {
		long id = sent(NotificationKind.MARKETING);
		report(id).andExpect(jsonPath("$.bySkipReason.STOPPED").value(1))
			.andExpect(jsonPath("$.bySkipReason.length()").value(1));
		// Who: the recipients list of a sent notification shows the reason per family.
		mockMvc.perform(get(URL + id + "/recipients").header("Authorization", bearer(token)))
			.andExpect(jsonPath("$.items[?(@.phone == '+91XXXXXX0002')].skipReason").value("STOPPED"));
	}

	@Test
	void hoursAreSchoolTime() throws Exception {
		long id = sent(NotificationKind.NOTICE);
		set(id, "+919811100001", "status = 'DELIVERED', read_at = ?", at("2026-10-07T04:35:00Z"));   // 10:05
		set(id, "+919811100002", "status = 'DELIVERED', read_at = ?", at("2026-10-07T04:55:00Z"));   // 10:25
		set(id, "+919811100004", "status = 'DELIVERED', read_at = ?", at("2026-10-07T14:40:00Z"));   // 20:10
		report(id).andExpect(jsonPath("$.readByHour", hasSize(2)))
			.andExpect(jsonPath("$.readByHour[0].reads").value(2))
			.andExpect(jsonPath("$.readByHour[1].hour").value("2026-10-07T20:00:00+05:30"));
	}

	@Test
	void resendFailedPutsTheSameRowsBack() throws Exception {
		long id = sent(NotificationKind.NOTICE);
		dispatcher.runOnce();   // all went (test mode): SENT
		set(id, "+919811100001", "status = 'FAILED', problem = 'PROVIDER_ERROR', attempts = 3, error = 'timeout'");
		set(id, "+919811100002", "status = 'FAILED', problem = 'WRONG_NUMBER', attempts = 3");
		set(id, "+919811100004", "status = 'FAILED', problem = 'NOT_ON_WHATSAPP', attempts = 1");
		int rowsBefore = jdbc.queryForObject("select count(*) from message_outbox", Integer.class);

		mockMvc.perform(post(URL + id + "/resend-failed").header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requeued").value(1));

		// Only the one worth a second try; no new row; the notification is sending again.
		assertThat(jdbc.queryForObject("select count(*) from message_outbox", Integer.class)).isEqualTo(rowsBefore);
		assertThat(jdbc.queryForMap("select status, attempts, problem, error from message_outbox "
				+ "where notification_id = ? and phone = '+919811100001'", id)).containsEntry("status", "QUEUED")
			.containsEntry("attempts", 0)
			.containsEntry("problem", null)
			.containsEntry("error", null);
		assertThat(jdbc.queryForMap("select status, note from notification where id = ?", id))
			.containsEntry("status", "SENDING")
			.containsEntry("note", "Resent 1 failed message.");

		worker.runOnce();
		dispatcher.runOnce();
		assertThat(jdbc.queryForObject("select status from notification where id = ?", String.class, id))
			.isEqualTo("SENT");
		// Nothing left worth a try: 0, and nothing changes.
		mockMvc.perform(post(URL + id + "/resend-failed").header("Authorization", bearer(token)))
			.andExpect(jsonPath("$.requeued").value(0));
	}

	@Test
	void resendWaitsForTheMorningInQuietHours() throws Exception {
		long id = sent(NotificationKind.NOTICE);
		set(id, "+919811100001", "status = 'FAILED', problem = 'PHONE_OFF', attempts = 3");
		clock.setInstant(Instant.parse("2026-10-07T17:00:00Z"));   // 22:30
		token = tokenFor(owner);
		mockMvc.perform(post(URL + id + "/resend-failed").header("Authorization", bearer(token)))
			.andExpect(jsonPath("$.requeued").value(1));
		assertThat(jdbc.queryForObject("select next_try_at from message_outbox where notification_id = ? "
				+ "and phone = '+919811100001'", Timestamp.class, id).toInstant())
			.isEqualTo(Instant.parse("2026-10-08T02:30:00Z"));   // 08:00
	}

	@Test
	void onlyASentNotificationCanBeResent() throws Exception {
		long templateId = jdbc.queryForObject("insert into message_template (kind, name, channel, body, approved_body, "
				+ "approval) values ('NOTICE', 'T', 'WHATSAPP', 'Hi', 'Hi', 'APPROVED') returning id", Long.class);
		long draft = notificationService.create(new NotificationRequest("Draft", NotificationKind.NOTICE, templateId,
				null, null, Map.of(), null, new Audience(List.of(AudienceGroup.CURRENT_PARENTS), null)),
				Role.OWNER.permissions(), owner.getId()).id();
		mockMvc.perform(post(URL + draft + "/resend-failed").header("Authorization", bearer(token)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("NOTIFICATION_NOT_EDITABLE"));
		// A draft has an empty report, not an error.
		report(draft).andExpect(jsonPath("$.rows").value(0)).andExpect(jsonPath("$.readByHour", hasSize(0)));
		mockMvc.perform(get(URL + "999999/report").header("Authorization", bearer(token)))
			.andExpect(status().isNotFound());
	}

}

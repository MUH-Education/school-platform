package com.muhjain.school.notification;

import com.muhjain.school.model.dto.request.NotificationRequest;
import com.muhjain.school.model.dto.request.PaymentRequest;
import com.muhjain.school.model.dto.request.SendNotificationRequest;
import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.PaymentLine;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.*;
import com.muhjain.school.service.fee.PaymentService;
import com.muhjain.school.service.messaging.OutboxWorker;
import com.muhjain.school.service.notification.NotificationDispatcher;
import com.muhjain.school.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code NotificationDispatcher} (task 11.9), end to end with the real service, guard chain and worker, on the small
 * school of {@link NotificationTestBase} (7 Oct 2026, 10 am): Punia (Aryan + Siya), Chauhan (stop list), Sharma.
 */
class NotificationDispatcherTest extends NotificationTestBase {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationDispatcher dispatcher;

	@Autowired
	private OutboxWorker worker;

	@Autowired
	private PaymentService paymentService;

	private AppUser owner;

	@BeforeEach
	void owner() {
		owner = addUser("+919812340001", Role.OWNER);
	}

	private long template(String kind, String body, String smsBody) {
		return jdbc.queryForObject("insert into message_template (kind, name, channel, body, approved_body, sms_body, "
				+ "approval) values (?, 'T', 'WHATSAPP', ?, ?, ?, 'APPROVED') returning id", Long.class, kind, body, body,
				smsBody);
	}

	private long draft(NotificationKind kind, String body, String smsBody, AudienceFilter filter,
			AudienceGroup... groups) {
		long templateId = template(TemplateKind.of(kind).name(), body, smsBody);
		return notificationService.create(new NotificationRequest("Test", kind, templateId, null, null,
				Map.of("Date", "10 अक्टूबर"), null, new Audience(List.of(groups), filter)), Role.OWNER.permissions(),
				owner.getId()).id();
	}

	private long noticeToParents() {
		return draft(NotificationKind.NOTICE, "{Child name} की PTM {Date} को है।", null, null,
				AudienceGroup.CURRENT_PARENTS);
	}

	private void sendNow(long id) {
		notificationService.send(id, new SendNotificationRequest(true, null), Role.OWNER.permissions(), owner.getId());
	}

	private void sendAt(long id, String at) {
		notificationService.send(id, new SendNotificationRequest(null, OffsetDateTime.parse(at)),
				Role.OWNER.permissions(), owner.getId());
	}

	private List<Map<String, Object>> rows(long id) {
		return jdbc.queryForList("select phone, channel, status, skip_reason, body, purpose, guardian_id, next_try_at "
				+ "from message_outbox where notification_id = ? order by phone, channel", id);
	}

	private Map<String, Object> notification(long id) {
		return jdbc.queryForMap("select status, recipient_count, expanded_at, finished_at, note from notification "
				+ "where id = ?", id);
	}

	@Test
	void sendNowOnlyChangesStatus_dispatcherWritesRows() {
		long id = noticeToParents();
		sendNow(id);
		assertThat(rows(id)).isEmpty();

		assertThat(dispatcher.runOnce()).isEqualTo(1);

		List<Map<String, Object>> rows = rows(id);
		assertThat(rows).extracting(r -> r.get("phone"))
			.containsExactly("+919811100001", "+919811100002", "+919811100004");
		assertThat(rows).allSatisfy(r -> {
			assertThat(r.get("status")).isEqualTo("QUEUED");
			assertThat(r.get("purpose")).isEqualTo("NOTICE");
			assertThat(r.get("channel")).isEqualTo("WHATSAPP");
		});
		// One row per family, the text filled for that family.
		assertThat(rows.getFirst().get("body")).isEqualTo("Aryan और Siya की PTM 10 अक्टूबर को है।");
		assertThat(rows.getFirst().get("guardian_id")).isEqualTo(punia);
		assertThat(notification(id)).containsEntry("status", "SENDING").containsEntry("recipient_count", 3);
		assertThat(notification(id).get("expanded_at")).isNotNull();
	}

	@Test
	void dispatcherExpandsScheduledWhenDue_once() {
		long id = noticeToParents();
		sendAt(id, "2026-10-07T10:30:00+05:30");
		dispatcher.runOnce();
		assertThat(rows(id)).isEmpty();   // 10:00, not yet

		clock.setInstant(Instant.parse("2026-10-07T05:01:00Z"));   // 10:31
		dispatcher.runOnce();
		assertThat(rows(id)).hasSize(3);
		dispatcher.runOnce();
		assertThat(rows(id)).hasSize(3);   // never twice
	}

	@Test
	void dispatcherClosesNotificationWhenNoQueuedLeft() {
		long id = noticeToParents();
		sendNow(id);
		dispatcher.runOnce();
		dispatcher.runOnce();
		assertThat(notification(id)).containsEntry("status", "SENDING");   // rows still QUEUED

		worker.runOnce();   // test mode: TEST_ONLY
		dispatcher.runOnce();
		assertThat(notification(id)).containsEntry("status", "SENT");
		assertThat(notification(id).get("finished_at")).isNotNull();
	}

	@Test
	void whenEveryRowFailedTheNotificationFailed() {
		long id = noticeToParents();
		sendNow(id);
		dispatcher.runOnce();
		jdbc.update("update message_outbox set status = 'FAILED', attempts = 3 where notification_id = ?", id);
		dispatcher.runOnce();
		assertThat(notification(id)).containsEntry("status", "FAILED");
	}

	@Test
	void stoppedFamilyIsASkippedRowNotAMessage() {
		long id = draft(NotificationKind.MARKETING, "{Parent name} जी, open day रविवार को।", null, null,
				AudienceGroup.CURRENT_PARENTS);
		sendNow(id);
		dispatcher.runOnce();
		Map<String, Object> chauhanRow = rows(id).get(1);
		assertThat(chauhanRow).containsEntry("phone", "+919811100002")
			.containsEntry("status", "SKIPPED")
			.containsEntry("skip_reason", "STOPPED")
			.containsEntry("purpose", "MARKETING");
		assertThat(notification(id)).containsEntry("recipient_count", 2);

		worker.runOnce();
		assertThat(jdbc.queryForObject("select status from message_outbox where notification_id = ? and phone = ?",
				String.class, id, "+919811100002")).isEqualTo("SKIPPED");
	}

	@Test
	void urgentGoesOnBothChannelsAndIgnoresQuietHours() {
		clock.setInstant(Instant.parse("2026-10-07T17:00:00Z"));   // 22:30 school time
		long id = draft(NotificationKind.URGENT, "{Child name}: school closed tomorrow.", "School closed tomorrow.",
				null, AudienceGroup.CURRENT_PARENTS);
		sendNow(id);
		dispatcher.runOnce();
		List<Map<String, Object>> rows = rows(id);
		assertThat(rows).hasSize(6);   // 3 families × WhatsApp and SMS
		assertThat(rows.subList(0, 2)).extracting(r -> r.get("channel")).containsExactly("SMS", "WHATSAPP");
		assertThat(rows.getFirst().get("body")).isEqualTo("School closed tomorrow.");
		// Not held for the night.
		assertThat(rows).allSatisfy(r -> assertThat(((java.sql.Timestamp) r.get("next_try_at")).toInstant())
			.isEqualTo(clock.instant()));
		assertThat(notification(id)).containsEntry("recipient_count", 3);
	}

	@Test
	void aGreetingAtNightWaitsForTheMorning() {
		long id = draft(NotificationKind.GREETING, "Happy Diwali, {Parent name} जी", null, null,
				AudienceGroup.CURRENT_PARENTS);
		sendNow(id);
		clock.setInstant(Instant.parse("2026-10-07T16:45:00Z"));   // 22:15: the dispatcher was late (server down)
		dispatcher.runOnce();
		assertThat(rows(id)).filteredOn(r -> "QUEUED".equals(r.get("status")))
			.allSatisfy(r -> assertThat(((java.sql.Timestamp) r.get("next_try_at")).toInstant())
				.isEqualTo(Instant.parse("2026-10-08T02:30:00Z")));   // 08:00 next day
		assertThat(worker.runOnce()).isZero();
	}

	@Test
	void feeReminderSkipsFamilyThatPaidAfterScheduling() {
		long id = draft(NotificationKind.FEE_REMINDER, "{Parent name} जी, {Months} की फ़ीस {Amount due} बाकी है।",
				null, null, AudienceGroup.FAMILIES_WITH_DUES);
		sendAt(id, "2026-10-07T10:30:00+05:30");
		// 10:10 the Sharma family pays everything at the office.
		jdbc.update("insert into fee_plan (student_id, session_id, from_month, school_fee_per_month, created_by) "
				+ "values (?, ?, date '2026-04-01', 3200, ?)", dev, sessionId, owner.getId());
		paymentService.record(dev, new PaymentRequest(null, PaymentMode.CASH, null,
				List.of(new PaymentLine(FeeHead.SCHOOL, new BigDecimal("9600")))), owner.getId());

		clock.setInstant(Instant.parse("2026-10-07T05:01:00Z"));   // 10:31
		dispatcher.runOnce();

		// Sharma is not even found any more (nothing due now); Punia gets the numbers of this moment.
		List<Map<String, Object>> rows = rows(id);
		assertThat(rows).extracting(r -> r.get("phone")).containsExactly("+919811100001");
		assertThat(rows.getFirst().get("body")).isEqualTo("Rajesh Punia जी, सितंबर और अक्टूबर की फ़ीस ₹8,500 बाकी है।");
	}

	@Test
	void aFamilyThatPaidTodayButStillOwesIsSkippedWithItsReason() {
		long id = draft(NotificationKind.FEE_REMINDER, "{Amount due} बाकी है।", null, null,
				AudienceGroup.FAMILIES_WITH_DUES);
		jdbc.update("insert into fee_plan (student_id, session_id, from_month, school_fee_per_month, created_by) "
				+ "values (?, ?, date '2026-04-01', 3200, ?)", dev, sessionId, owner.getId());
		paymentService.record(dev, new PaymentRequest(null, PaymentMode.CASH, null,
				List.of(new PaymentLine(FeeHead.SCHOOL, new BigDecimal("3200")))), owner.getId());
		sendNow(id);
		dispatcher.runOnce();
		assertThat(jdbc.queryForMap("select status, skip_reason from message_outbox where notification_id = ? "
				+ "and phone = '+919811100004'", id)).containsEntry("status", "SKIPPED")
			.containsEntry("skip_reason", "ALREADY_PAID");
	}

	@Test
	void nobodyLeftClosesAtOnceWithANote() {
		long id = draft(NotificationKind.FEE_REMINDER, "{Amount due} बाकी है।", null,
				new AudienceFilter(List.of("10"), null, null, null, null, null, null, null, null, null),
				AudienceGroup.FAMILIES_WITH_DUES);
		sendAt(id, "2026-10-07T10:30:00+05:30");
		jdbc.update("delete from fee_due where student_id = ?", dev);   // paid off another way
		clock.setInstant(Instant.parse("2026-10-07T05:01:00Z"));
		dispatcher.runOnce();
		assertThat(notification(id)).containsEntry("status", "SENT")
			.containsEntry("recipient_count", 0)
			.containsEntry("note", "Nobody got it: everyone was skipped. See the report for the reasons.");
	}

	@Test
	void aCancelledNotificationIsNeverExpanded() {
		long id = noticeToParents();
		sendAt(id, "2026-10-07T10:30:00+05:30");
		notificationService.cancel(id, Role.OWNER.permissions(), owner.getId());
		clock.setInstant(Instant.parse("2026-10-07T05:01:00Z"));
		assertThat(dispatcher.runOnce()).isZero();
		assertThat(rows(id)).isEmpty();
	}

}

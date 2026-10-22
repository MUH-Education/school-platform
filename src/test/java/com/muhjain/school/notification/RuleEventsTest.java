package com.muhjain.school.notification;

import com.muhjain.school.model.dto.request.AdmissionRequest;
import com.muhjain.school.model.dto.request.GuardianRequest;
import com.muhjain.school.model.dto.request.PaymentRequest;
import com.muhjain.school.model.dto.response.PaymentLine;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.FatherOccupation;
import com.muhjain.school.model.enums.FeeHead;
import com.muhjain.school.model.enums.Gender;
import com.muhjain.school.model.enums.GuardianRelation;
import com.muhjain.school.model.enums.PaymentMode;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.service.fee.PaymentService;
import com.muhjain.school.service.notification.NotificationDispatcher;
import com.muhjain.school.service.notification.RuleService;
import com.muhjain.school.service.student.AdmissionService;
import com.muhjain.school.model.dto.response.RuleFiring;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The event rules (task 12.5): WELCOME after an admission, BUS_LATE from the attendant's button, and PAYMENT_SAVED
 * skipping a waiting fee reminder. On the small school of {@link NotificationTestBase} (Wednesday 7 Oct 2026, 10 am).
 */
class RuleEventsTest extends NotificationTestBase {

	@Autowired
	private AdmissionService admissionService;

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private RuleService ruleService;

	@Autowired
	private NotificationDispatcher dispatcher;

	@Autowired
	private TransactionTemplate tx;

	private AppUser owner;

	@BeforeEach
	void owner() {
		owner = addUser("+919812340001", Role.OWNER);
	}

	private long template(String kind, String body) {
		return jdbc.queryForObject("insert into message_template (kind, name, channel, body, approved_body, approval) "
				+ "values (?, 'T', 'WHATSAPP', ?, ?, 'APPROVED') returning id", Long.class, kind, body, body);
	}

	private void turnOn(String code, long templateId) {
		jdbc.update("update notification_rule set template_id = ?, enabled = true, updated_by = ? where code = ?",
				templateId, owner.getId(), code);
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	private AdmissionRequest admission(String name, String phone) {
		return new AdmissionRequest(name, LocalDate.of(2019, 3, 2), Gender.M, "1", null, "Tohana", null,
				FatherOccupation.OTHER, null,
				List.of(new GuardianRequest("Mahesh Goyal", phone, GuardianRelation.FATHER, null)), null, null, null,
				null, null);
	}

	// ---------------------------------------------------------------- WELCOME

	@Test
	void welcomeRuleSendsToTheNewFamilyNextMorning() {
		turnOn("WELCOME", template("ONE_FAMILY", "{Parent name} जी, MUH Jain School में {Child name} का स्वागत है।"));

		long studentId = admissionService.admit(admission("Kabir Goyal", "+919811100020"), owner.getId()).studentId();

		Map<String, Object> made = jdbc.queryForMap("select n.kind, n.status, n.scheduled_at, n.source, "
				+ "n.audience -> 'filter' -> 'familyIds' ->> 0 as family from notification n");
		long family = jdbc.queryForObject("select guardian_id from student_guardian where student_id = ?", Long.class,
				studentId);
		assertThat(made).containsEntry("kind", "ONE_FAMILY")
			.containsEntry("status", "SCHEDULED")
			.containsEntry("source", "RULE")
			.containsEntry("family", String.valueOf(family));
		// Admitted on Wednesday 10 am → Thursday 9:00 school time.
		assertThat(((java.sql.Timestamp) made.get("scheduled_at")).toInstant())
			.isEqualTo(Instant.parse("2026-10-08T03:30:00Z"));
		assertThat(jdbc.queryForObject("select run_key from notification_rule_run", String.class))
			.isEqualTo("student:" + studentId);
	}

	@Test
	void welcomeRuleIsNotFiredWhenAdmissionRollsBack() {
		turnOn("WELCOME", template("ONE_FAMILY", "{Parent name} जी, {Child name} का स्वागत है।"));

		// The admission itself goes through, then the transaction around it is rolled back.
		tx.executeWithoutResult(status -> {
			admissionService.admit(admission("Kabir Goyal", "+919811100020"), owner.getId());
			status.setRollbackOnly();
		});

		assertThat(count("select count(*) from student where name = 'Kabir Goyal'")).isZero();
		assertThat(count("select count(*) from notification")).isZero();
		assertThat(count("select count(*) from notification_rule_run")).isZero();
	}

	@Test
	void anAdmissionStillWorksWhenTheWelcomeCannotBeMade() {
		// The rule is on, but its template is not of the ONE_FAMILY kind: the welcome fails, the admission does not.
		turnOn("WELCOME", template("NOTICE", "स्वागत है।"));

		admissionService.admit(admission("Kabir Goyal", "+919811100020"), owner.getId());

		assertThat(count("select count(*) from student where name = 'Kabir Goyal'")).isEqualTo(1);
		assertThat(count("select count(*) from notification")).isZero();
	}

	@Test
	void welcomeIsOffByDefault() {
		admissionService.admit(admission("Kabir Goyal", "+919811100020"), owner.getId());
		assertThat(count("select count(*) from notification")).isZero();
	}

	// ---------------------------------------------------------------- BUS_LATE

	private AppUser balwanUser() {
		AppUser user = new AppUser("+919812340020", Role.ATTENDANT);
		user.setStaffId(balwan);
		return userRepository.saveAndFlush(user);
	}

	private ResultActions runningLate(String token, long routeId, String body) throws Exception {
		var request = post("/api/v1/trips/" + routeId + "/running-late").contentType(MediaType.APPLICATION_JSON)
			.content(body);
		if (token != null) {
			request.header("Authorization", bearer(token));
		}
		return mockMvc.perform(request);
	}

	/** A second route, Route 9 with its own van, and Dev (Sharma family) on it. */
	private long route9WithDev() {
		long van = addVehicle("Van 9");
		long route9 = jdbc.queryForObject("insert into route (name, vehicle_id) values ('Route 9', ?) returning id",
				Long.class, van);
		long stop = jdbc.queryForObject("insert into route_stop (route_id, name, seq_no, morning_time, evening_time) "
				+ "values (?, 'Tohana', 1, time '07:30', time '15:10') returning id", Long.class, route9);
		jdbc.update("insert into transport_enrolment (student_id, uses_bus, route_id, stop_id, from_date, "
				+ "bus_fee_per_month, reason, created_by) values (?, true, ?, ?, date '2026-04-01', 800, 'ADMISSION', ?)",
				dev, route9, stop, anyUserId());
		return route9;
	}

	@Test
	void busLateEventGoesOnlyToThatRouteToday() throws Exception {
		turnOn("BUS_LATE", template("URGENT", "{Route} की बस {Minutes} मिनट देर से है।"));
		long route9 = route9WithDev();
		clock.setInstant(Instant.parse("2026-10-07T02:05:00Z"));   // 7:35 am
		String token = tokenFor(balwanUser());

		runningLate(token, route4, "{\"minutes\": 20}").andExpect(status().isOk())
			.andExpect(jsonPath("$.routeId").value(route4))
			.andExpect(jsonPath("$.minutes").value(20))
			.andExpect(jsonPath("$.willNotify").value(true))
			.andExpect(jsonPath("$.alreadySent").value(false));

		Map<String, Object> made = jdbc.queryForMap("select id, kind, status, fixed_values ->> 'Minutes' as minutes "
				+ "from notification");
		assertThat(made).containsEntry("kind", "URGENT").containsEntry("status", "SENDING").containsEntry("minutes", "20");
		dispatcher.runOnce();
		// Only the Punia family (Aryan, Route 4); the Sharma family is on Route 9.
		assertThat(jdbc.queryForList("select distinct phone from message_outbox where notification_id = ?",
				String.class, made.get("id")))
			.containsExactly("+919811100001");
		assertThat(jdbc.queryForObject("select body from message_outbox where notification_id = ? limit 1",
				String.class, made.get("id")))
			.isEqualTo("Route 4 की बस 20 मिनट देर से है।");

		// The same morning again: nothing new.
		runningLate(token, route4, "{\"minutes\": 30}").andExpect(status().isOk())
			.andExpect(jsonPath("$.willNotify").value(false))
			.andExpect(jsonPath("$.alreadySent").value(true));
		assertThat(count("select count(*) from notification")).isEqualTo(1);

		// Balwan works on Route 4, not Route 9.
		runningLate(token, route9, "{\"minutes\": 20}").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("NOT_YOUR_ROUTE"));
		assertThat(count("select count(*) from notification")).isEqualTo(1);
	}

	@Test
	void busLateWithTheRuleOffSendsNothing() throws Exception {
		String token = tokenFor(balwanUser());
		runningLate(token, route4, "{\"minutes\": 20}").andExpect(status().isOk())
			.andExpect(jsonPath("$.willNotify").value(false))
			.andExpect(jsonPath("$.alreadySent").value(false));
		assertThat(count("select count(*) from notification")).isZero();
	}

	@Test
	void runningLateNeedsSensibleMinutes() throws Exception {
		String token = tokenFor(balwanUser());
		runningLate(token, route4, "{\"minutes\": 2}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"));
		runningLate(token, route4, "{}").andExpect(status().isBadRequest());
	}

	@Test
	void runningLateNeedsALoginAndTheAttendantPermission() throws Exception {
		runningLate(null, route4, "{\"minutes\": 20}").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
		// The office has TRIPS_RECORD_ANY (corrections), not the attendant's TRIPS_RECORD.
		String office = tokenFor(addUser("+919812340002", Role.OFFICE_ADMIN));
		runningLate(office, route4, "{\"minutes\": 20}").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("FORBIDDEN"));
	}

	// ---------------------------------------------------------------- PAYMENT_SAVED

	@Test
	void paymentSavedSkipsQueuedFeeReminderAtOnce() {
		turnOn("FEE_REMINDER_10TH", template("FEE_REMINDER", "{Parent name} जी, {Amount due} बाकी है।"));
		long notification = ruleService.fire(
				jdbc.queryForObject("select id from notification_rule where code = 'FEE_REMINDER_10TH'", Long.class),
				RuleFiring.of("2026-10-10")).notificationId();
		dispatcher.runOnce();
		assertThat(jdbc.queryForList("select status from message_outbox where notification_id = ? order by phone",
				String.class, notification))
			.containsExactly("QUEUED", "QUEUED");

		// Anil Sharma pays before the worker reaches his row.
		jdbc.update("insert into fee_plan (student_id, session_id, from_month, school_fee_per_month, created_by) "
				+ "values (?, ?, date '2026-04-01', 3200, ?)", dev, sessionId, owner.getId());
		paymentService.record(dev, new PaymentRequest(null, PaymentMode.CASH, null,
				List.of(new PaymentLine(FeeHead.SCHOOL, new BigDecimal("3200")))), owner.getId());

		List<Map<String, Object>> rows = jdbc.queryForList("select phone, status, skip_reason from message_outbox "
				+ "where notification_id = ? order by phone", notification);
		assertThat(rows.get(0)).containsEntry("phone", "+919811100001").containsEntry("status", "QUEUED");
		assertThat(rows.get(1)).containsEntry("phone", "+919811100004")
			.containsEntry("status", "SKIPPED")
			.containsEntry("skip_reason", "ALREADY_PAID");
	}

	@Test
	void aPaymentThatRollsBackSkipsNothing() {
		turnOn("FEE_REMINDER_10TH", template("FEE_REMINDER", "{Amount due} बाकी है।"));
		long notification = ruleService.fire(
				jdbc.queryForObject("select id from notification_rule where code = 'FEE_REMINDER_10TH'", Long.class),
				RuleFiring.of("2026-10-10")).notificationId();
		dispatcher.runOnce();
		jdbc.update("insert into fee_plan (student_id, session_id, from_month, school_fee_per_month, created_by) "
				+ "values (?, ?, date '2026-04-01', 3200, ?)", dev, sessionId, owner.getId());

		tx.executeWithoutResult(status -> {
			paymentService.record(dev, new PaymentRequest(null, PaymentMode.CASH, null,
					List.of(new PaymentLine(FeeHead.SCHOOL, new BigDecimal("3200")))), owner.getId());
			status.setRollbackOnly();
		});

		assertThat(jdbc.queryForList("select status from message_outbox where notification_id = ?", String.class,
				notification))
			.containsOnly("QUEUED");
	}

}

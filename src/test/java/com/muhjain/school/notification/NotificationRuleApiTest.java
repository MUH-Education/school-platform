package com.muhjain.school.notification;

import com.jayway.jsonpath.JsonPath;
import com.muhjain.school.model.dto.request.PaymentRequest;
import com.muhjain.school.model.dto.response.PaymentLine;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.FeeHead;
import com.muhjain.school.model.enums.PaymentMode;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.service.fee.PaymentService;
import com.muhjain.school.service.notification.NotificationDispatcher;
import com.muhjain.school.service.notification.RuleScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Settings screen's "Automatic messages" endpoints (task 12.7), on the small school of
 * {@link NotificationTestBase} (Wednesday 7 Oct 2026, 10 am).
 */
class NotificationRuleApiTest extends NotificationTestBase {

	private static final String RULES = "/api/v1/notification-rules";

	@Autowired
	private RuleScheduler scheduler;

	@Autowired
	private NotificationDispatcher dispatcher;

	@Autowired
	private PaymentService paymentService;

	private AppUser owner;

	private String ownerToken;

	@BeforeEach
	void owner() {
		owner = addUser("+919812340001", Role.OWNER);
		ownerToken = tokenFor(owner);
	}

	private long rule(String code) {
		return jdbc.queryForObject("select id from notification_rule where code = ?", Long.class, code);
	}

	private long template(String kind, String body, String approval) {
		return jdbc.queryForObject("insert into message_template (kind, name, channel, body, approved_body, approval) "
				+ "values (?, 'Fee tone 1', 'WHATSAPP', ?, ?, ?) returning id", Long.class, kind, body,
				"APPROVED".equals(approval) ? body : null, approval);
	}

	private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
		if (token != null) {
			request.header("Authorization", bearer(token));
		}
		return mockMvc.perform(request);
	}

	private ResultActions change(String code, String json) throws Exception {
		return call(put(RULES + "/" + rule(code)).contentType(MediaType.APPLICATION_JSON).content(json), ownerToken);
	}

	private ResultActions runNow(String code) throws Exception {
		return call(post(RULES + "/" + rule(code) + "/run-now"), ownerToken);
	}

	// ---------------------------------------------------------------- the table

	@Test
	void theTableShowsEveryRuleWithItsChoices() throws Exception {
		call(get(RULES), ownerToken).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(8)))
			.andExpect(jsonPath("$[0].code").value("FEE_REMINDER_10TH"))
			.andExpect(jsonPath("$[0].enabled").value(false))
			.andExpect(jsonPath("$[0].templateId").doesNotExist())
			.andExpect(jsonPath("$[0].schedule.type").value("MONTHLY"))
			.andExpect(jsonPath("$[0].schedule.time").value("10:00"))
			.andExpect(jsonPath("$[0].schedule.daysOfMonth", contains(10)))
			.andExpect(jsonPath("$[0].lastMonthNotifications").value(0))
			.andExpect(jsonPath("$[2].schedule.type").value("DAILY"))
			.andExpect(jsonPath("$[2].schedule.time").value("06:00"))
			.andExpect(jsonPath("$[3].code").value("FESTIVAL"))
			.andExpect(jsonPath("$[3].festivals", hasSize(0)))
			.andExpect(jsonPath("$[4].code").value("WELCOME"))
			.andExpect(jsonPath("$[4].triggerType").value("EVENT"))
			.andExpect(jsonPath("$[4].schedule").doesNotExist())
			.andExpect(jsonPath("$[4].festivals").doesNotExist())
			.andExpect(jsonPath("$[6].code").value("NO_MORNING_TAP"))
			.andExpect(jsonPath("$[6].schedule.type").value("WEEKLY"))
			.andExpect(jsonPath("$[6].schedule.daysOfWeek", hasSize(6)));
	}

	// ---------------------------------------------------------------- turning on and off

	@Test
	void aRuleTurnsOnOnlyWithAnApprovedTemplateOfItsKind() throws Exception {
		change("FEE_REMINDER_10TH", "{\"enabled\": true}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("RULE_NEEDS_TEMPLATE"));
		long notice = template("NOTICE", "PTM है।", "APPROVED");
		change("FEE_REMINDER_10TH", "{\"templateId\": " + notice + "}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("TEMPLATE_NOT_FOR_KIND"));
		long waiting = template("FEE_REMINDER", "{Amount due} बाकी है।", "WAITING");
		change("FEE_REMINDER_10TH", "{\"templateId\": " + waiting + ", \"enabled\": true}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("TEMPLATE_NOT_APPROVED"));
		// Nothing of a refused change is kept.
		assertThat(jdbc.queryForObject("select template_id from notification_rule where code = 'FEE_REMINDER_10TH'",
				Long.class))
			.isNull();

		long approved = template("FEE_REMINDER", "{Amount due} बाकी है।", "APPROVED");
		change("FEE_REMINDER_10TH", "{\"templateId\": " + approved + ", \"enabled\": true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled").value(true))
			.andExpect(jsonPath("$.templateId").value(approved))
			.andExpect(jsonPath("$.templateName").value("Fee tone 1"))
			.andExpect(jsonPath("$.updatedBy").value(owner.getId()));
		assertThat(jdbc.queryForObject("select summary from audit_log where entity_type = 'NOTIFICATION_RULE' "
				+ "order by id desc limit 1", String.class))
			.isEqualTo("Automatic message \"Fee reminder 10th\": template \"Fee tone 1\", turned on.");
	}

	@Test
	void turningARuleOffStopsIt() throws Exception {
		change("FEE_REMINDER_10TH", "{\"templateId\": " + template("FEE_REMINDER", "{Amount due} बाकी है।", "APPROVED")
				+ ", \"enabled\": true}")
			.andExpect(status().isOk());
		change("FEE_REMINDER_10TH", "{\"enabled\": false}").andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled").value(false));

		Instant tenOct = Instant.parse("2026-10-10T04:30:05Z");
		clock.setInstant(tenOct);
		assertThat(scheduler.runOnce(tenOct)).isZero();
	}

	@Test
	void aRuleWithNobodyToSendToCannotBeOn() throws Exception {
		long notice = template("NOTICE", "कागज़ जमा करें।", "APPROVED");
		change("DOCUMENTS_MISSING_MONDAY", "{\"templateId\": " + notice + ", \"enabled\": true}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("RULE_NEEDS_AUDIENCE"));
	}

	// ---------------------------------------------------------------- time, audience, festivals

	@Test
	void theTimeIsAChoiceAndTheServerBuildsTheCron() throws Exception {
		change("DOCUMENTS_MISSING_MONDAY",
				"{\"schedule\": {\"type\": \"WEEKLY\", \"time\": \"09:15\", \"daysOfWeek\": [\"MONDAY\", \"THURSDAY\"]}}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cron").value("0 15 9 * * MON,THU"))
			.andExpect(jsonPath("$.schedule.type").value("WEEKLY"));
		change("FEE_REMINDER_10TH", "{\"schedule\": {\"type\": \"MONTHLY\", \"time\": \"11:30\", \"daysOfMonth\": [5]}}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cron").value("0 30 11 5 * *"));
		// The 31st does not happen every month.
		change("FEE_REMINDER_10TH", "{\"schedule\": {\"type\": \"MONTHLY\", \"time\": \"10:00\", \"daysOfMonth\": [31]}}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"));
		change("FEE_REMINDER_10TH", "{\"schedule\": {\"type\": \"WEEKLY\", \"time\": \"10:00\"}}")
			.andExpect(status().isBadRequest());
		// An event rule has no time and takes its audience from the event.
		change("WELCOME", "{\"schedule\": {\"type\": \"DAILY\", \"time\": \"09:00\"}}").andExpect(status().isBadRequest());
		change("BUS_LATE", "{\"audience\": {\"groups\": [\"CURRENT_PARENTS\"]}}").andExpect(status().isBadRequest());
		change("WELCOME", "{\"delayMinutes\": 30}").andExpect(status().isOk())
			.andExpect(jsonPath("$.delayMinutes").value(30));
		change("FEE_REMINDER_10TH", "{\"delayMinutes\": 30}").andExpect(status().isBadRequest());
	}

	@Test
	void theAudienceIsCheckedAgainstTheKind() throws Exception {
		change("DOCUMENTS_MISSING_MONDAY", "{\"audience\": {\"groups\": [\"ENQUIRY_OPEN\"]}}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("AUDIENCE_NOT_ALLOWED"));
		change("DOCUMENTS_MISSING_MONDAY", "{\"audience\": {\"groups\": [\"CURRENT_PARENTS\"], \"filter\": "
				+ "{\"classes\": [\"10\"]}}}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.audience.groups", contains("CURRENT_PARENTS")))
			.andExpect(jsonPath("$.audience.filter.classes", contains("10")));
	}

	@Test
	void festivalDatesAreSetAsAList() throws Exception {
		change("FESTIVAL", "{\"festivals\": [{\"date\": \"2027-03-04\", \"name\": \"होली\"}, "
				+ "{\"date\": \"2026-11-08\", \"name\": \"दीपावली\"}]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.festivals", hasSize(2)))
			.andExpect(jsonPath("$.festivals[0].date").value("2026-11-08"))
			.andExpect(jsonPath("$.festivals[0].name").value("दीपावली"));
		assertThat(jdbc.queryForObject("select fixed_values -> 'Festival' ->> '2027-03-04' from notification_rule "
				+ "where code = 'FESTIVAL'", String.class))
			.isEqualTo("होली");
		change("FEE_REMINDER_10TH", "{\"festivals\": [{\"date\": \"2027-03-04\", \"name\": \"होली\"}]}")
			.andExpect(status().isBadRequest());
	}

	// ---------------------------------------------------------------- run now

	@Test
	void runNowFiresOnceMadeByTheCaller() throws Exception {
		change("FEE_REMINDER_10TH", "{\"templateId\": " + template("FEE_REMINDER", "{Amount due} बाकी है।", "APPROVED")
				+ "}")
			.andExpect(status().isOk());
		// Off, but "Run now" still fires it.
		AppUser secondOwner = addUser("+919812340009", Role.OWNER);
		String body = call(post(RULES + "/" + rule("FEE_REMINDER_10TH") + "/run-now"), tokenFor(secondOwner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CREATED"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long id = ((Number) JsonPath.read(body, "$.notificationId")).longValue();
		assertThat(jdbc.queryForObject("select created_by from notification where id = ?", Long.class, id))
			.isEqualTo(secondOwner.getId());
		assertThat(jdbc.queryForObject("select count(*) from notification", Integer.class)).isEqualTo(1);

		// Home shows who sent it: "Automatic · Fee reminder 10th".
		call(get("/api/v1/notifications/" + id), ownerToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("RULE"))
			.andExpect(jsonPath("$.ruleId").value(rule("FEE_REMINDER_10TH")))
			.andExpect(jsonPath("$.ruleName").value("Fee reminder 10th"));
		call(get("/api/v1/notifications"), ownerToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].ruleName").value("Fee reminder 10th"));

		// A run by hand does not block the timed run of the 10th.
		Instant tenOct = Instant.parse("2026-10-10T04:30:05Z");
		jdbc.update("update notification_rule set enabled = true where code = 'FEE_REMINDER_10TH'");
		clock.setInstant(tenOct);
		assertThat(scheduler.runOnce(tenOct)).isEqualTo(1);
	}

	@Test
	void runNowNeedsATemplateAndATimedRule() throws Exception {
		runNow("FEE_REMINDER_10TH").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("RULE_NEEDS_TEMPLATE"));
		runNow("WELCOME").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("RULE_NEEDS_EVENT"));
		call(post(RULES + "/999999/run-now"), ownerToken).andExpect(status().isNotFound());
	}

	// ---------------------------------------------------------------- the "Done when" of the phase

	@Test
	void onTheTenthAtTenTheFeeReminderSkipsPaidFamiliesAndTheReportSaysWhy() throws Exception {
		change("FEE_REMINDER_10TH", "{\"templateId\": " + template("FEE_REMINDER", "{Amount due} बाकी है।", "APPROVED")
				+ ", \"enabled\": true}")
			.andExpect(status().isOk());
		Instant tenOct = Instant.parse("2026-10-10T04:30:00Z");   // 10:00 school time
		clock.setInstant(tenOct.minus(Duration.ofMinutes(30)));
		// The Sharma family pays at 9:30 but still owes August and September.
		jdbc.update("insert into fee_plan (student_id, session_id, from_month, school_fee_per_month, created_by) "
				+ "values (?, ?, date '2026-04-01', 3200, ?)", dev, sessionId, owner.getId());
		paymentService.record(dev, new PaymentRequest(null, PaymentMode.CASH, null,
				List.of(new PaymentLine(FeeHead.SCHOOL, new BigDecimal("3200")))), owner.getId());

		clock.setInstant(tenOct);
		assertThat(scheduler.runOnce(tenOct)).isEqualTo(1);
		dispatcher.runOnce();

		long id = jdbc.queryForObject("select id from notification where source = 'RULE'", Long.class);
		// Three days later the old login has timed out (15 minutes idle): log in again.
		ownerToken = tokenFor(owner);
		call(get("/api/v1/notifications/" + id + "/report"), ownerToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.skipped").value(1))
			.andExpect(jsonPath("$.bySkipReason.ALREADY_PAID").value(1));
		call(get(RULES), ownerToken).andExpect(jsonPath("$[0].lastMonthNotifications").value(1))
			.andExpect(jsonPath("$[0].lastRunAt").value("2026-10-10T10:00:00+05:30"));
	}

	// ---------------------------------------------------------------- security

	@Test
	void everyEndpointNeedsALogin() throws Exception {
		call(get(RULES), null).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
		call(put(RULES + "/" + rule("BIRTHDAY")).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": false}"),
				null)
			.andExpect(status().isUnauthorized());
		call(post(RULES + "/" + rule("BIRTHDAY") + "/run-now"), null).andExpect(status().isUnauthorized());
	}

	@Test
	void theOfficeMayNotSeeOrChangeTheRules() throws Exception {
		String office = tokenFor(addUser("+919812340002", Role.OFFICE_ADMIN));
		call(get(RULES), office).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
		call(put(RULES + "/" + rule("BIRTHDAY")).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": false}"),
				office)
			.andExpect(status().isForbidden());
		call(post(RULES + "/" + rule("BIRTHDAY") + "/run-now"), office).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("select count(*) from notification", Integer.class)).isZero();
	}

}

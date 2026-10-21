package com.muhjain.school.notification;

import com.jayway.jsonpath.JsonPath;
import com.muhjain.school.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/v1/notifications} (task 11.8) on the small school of {@link NotificationTestBase}, 7 Oct 2026, 10 am:
 * three families of current parents (Punia, Chauhan on the stop list, Sharma), Mohan with no phone.
 */
class NotificationApiTest extends NotificationTestBase {

	private static final String URL = "/api/v1/notifications";

	private String owner;

	private long noticeTemplate;

	@BeforeEach
	void ownerAndTemplate() {
		owner = tokenFor(addUser("+919812340001", Role.OWNER));
		noticeTemplate = template("NOTICE", "{Child name} की PTM {Date} को है।", "APPROVED");
	}

	private long template(String kind, String body, String approval) {
		return jdbc.queryForObject("insert into message_template (kind, name, channel, body, approved_body, approval, "
				+ "example_values) values (?, 'PTM', 'WHATSAPP', ?, ?, ?, '{\"Child name\": \"Aryan\"}'::jsonb) "
				+ "returning id", Long.class, kind, body, "APPROVED".equals(approval) ? body : null, approval);
	}

	private ResultActions call(MockHttpServletRequestBuilder builder, String json) throws Exception {
		if (json != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mockMvc.perform(builder.header("Authorization", bearer(owner)));
	}

	private static String draft(String kind, long templateId, String audience, String extra) {
		return "{\"name\":\"PTM\",\"kind\":\"" + kind + "\",\"templateId\":" + templateId
				+ ",\"fixedValues\":{\"Date\":\"10 अक्टूबर\"},\"audience\":" + audience + extra + "}";
	}

	private static final String PARENTS = "{\"groups\":[\"CURRENT_PARENTS\"]}";

	private long create(String json) throws Exception {
		String answer = call(post(URL), json).andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(answer, "$.id")).longValue();
	}

	private long noticeDraft() throws Exception {
		return create(draft("NOTICE", noticeTemplate, PARENTS, ""));
	}

	private int outboxRows() {
		return jdbc.queryForObject("select count(*) from message_outbox", Integer.class);
	}

	// ---------------------------------------------------------------- draft

	@Test
	void aDraftAnswersWhoGetsIt() throws Exception {
		call(post(URL), draft("NOTICE", noticeTemplate, PARENTS, "")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.body").value("{Child name} की PTM {Date} को है।"))
			.andExpect(jsonPath("$.fixedValues.Date").value("10 अक्टूबर"))
			.andExpect(jsonPath("$.templateName").value("PTM"))
			.andExpect(jsonPath("$.source").value("MANUAL"))
			// 4 picked (Mohan has no phone) → 3. A notice reaches Chauhan although they said stop.
			.andExpect(jsonPath("$.counts.picked").value(4))
			.andExpect(jsonPath("$.counts.noPhone").value(1))
			.andExpect(jsonPath("$.counts.stopped").value(0))
			.andExpect(jsonPath("$.counts.total").value(3));
		assertThat(outboxRows()).isZero();
	}

	@Test
	void draftChecks() throws Exception {
		// A notice cannot go to enquiries.
		call(post(URL), draft("NOTICE", noticeTemplate, "{\"groups\":[\"ENQUIRY_OPEN\"]}", ""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("AUDIENCE_NOT_ALLOWED"));
		// A marketing message needs a marketing template.
		call(post(URL), draft("MARKETING", noticeTemplate, PARENTS, "")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("TEMPLATE_NOT_FOR_KIND"));
		// {Date} is not filled by the recipients: it needs a value.
		call(post(URL), "{\"name\":\"PTM\",\"kind\":\"NOTICE\",\"templateId\":" + noticeTemplate + ",\"audience\":"
				+ PARENTS + "}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.fixedValues").value("needs a value for {Date}"));
		// The text of an approved template is not changed here.
		call(post(URL), draft("NOTICE", noticeTemplate, PARENTS, ",\"body\":\"Something else\""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.body").exists());
		// A template with no picture cannot carry one.
		call(post(URL), draft("NOTICE", noticeTemplate, PARENTS, ",\"mediaKey\":\"notifications/a.png\""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void onlyTheOwnerMayWriteOrSendForNow() throws Exception {
		String office = tokenFor(addUser("+919812340002", Role.OFFICE_ADMIN));
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
			.content(draft("NOTICE", noticeTemplate, PARENTS, ""))
			.header("Authorization", bearer(office))).andExpect(status().isForbidden());
		mockMvc.perform(get(URL).header("Authorization", bearer(office))).andExpect(status().isForbidden());
	}

	@Test
	void aDraftCanBeChangedUntilItIsSent() throws Exception {
		long id = noticeDraft();
		call(put(URL + "/" + id), draft("NOTICE", noticeTemplate, "{\"groups\":[\"CURRENT_PARENTS\"],"
				+ "\"filter\":{\"classes\":[\"10\"]}}", "")).andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.total").value(2));   // Chauhan and Sharma
		call(post(URL + "/" + id + "/send"), "{\"sendNow\":true}").andExpect(status().isOk());
		call(put(URL + "/" + id), draft("NOTICE", noticeTemplate, PARENTS, "")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("NOTIFICATION_NOT_EDITABLE"));
	}

	// ---------------------------------------------------------------- send

	@Test
	void sendNowOnlyChangesStatus_dispatcherWritesRows() throws Exception {
		long id = noticeDraft();
		call(post(URL + "/" + id + "/send"), "{\"sendNow\":true}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SENDING"))
			.andExpect(jsonPath("$.expandedAt").doesNotExist())
			.andExpect(jsonPath("$.counts").doesNotExist());
		// The request wrote no outbox row: the dispatcher does that (task 11.9).
		assertThat(outboxRows()).isZero();
	}

	@Test
	void sendLaterChecksTheTime() throws Exception {
		long id = noticeDraft();
		call(post(URL + "/" + id + "/send"), "{}").andExpect(status().isBadRequest());
		call(post(URL + "/" + id + "/send"), "{\"sendNow\":true,\"scheduledAt\":\"2026-10-10T10:00:00+05:30\"}")
			.andExpect(status().isBadRequest());
		call(post(URL + "/" + id + "/send"), "{\"scheduledAt\":\"2026-10-06T10:00:00+05:30\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("SCHEDULE_IN_PAST"));
		call(post(URL + "/" + id + "/send"), "{\"scheduledAt\":\"2026-10-10T10:00:00+05:30\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SCHEDULED"))
			.andExpect(jsonPath("$.scheduledAt").value("2026-10-10T10:00:00+05:30"));
		assertThat(outboxRows()).isZero();
	}

	@Test
	void otherKindsRefuseQuietHoursSchedule() throws Exception {
		long id = noticeDraft();
		call(post(URL + "/" + id + "/send"), "{\"scheduledAt\":\"2026-10-07T22:30:00+05:30\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("SCHEDULE_IN_QUIET_HOURS"))
			.andExpect(jsonPath("$.message").value("Between 21:00 and 08:00 nothing is sent. Pick 08:00 or later."));
		// URGENT ignores quiet hours.
		long urgentTemplate = template("URGENT", "{Child name} की बस 20 मिनट देर से आएगी।", "APPROVED");
		long urgent = create("{\"name\":\"Bus late\",\"kind\":\"URGENT\",\"templateId\":" + urgentTemplate
				+ ",\"audience\":" + PARENTS + "}");
		call(post(URL + "/" + urgent + "/send"), "{\"scheduledAt\":\"2026-10-07T22:30:00+05:30\"}")
			.andExpect(status().isOk());
	}

	@Test
	void templateMustBeApprovedToSend() throws Exception {
		long waiting = template("NOTICE", "{Child name} की PTM {Date} को है।", "WAITING");
		long id = create(draft("NOTICE", waiting, PARENTS, ""));
		call(post(URL + "/" + id + "/send"), "{\"sendNow\":true}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("TEMPLATE_NOT_APPROVED"));
	}

	@Test
	void freeTextOneFamilyMayBeSentWithoutApproval() throws Exception {
		long freeText = template("ONE_FAMILY", "x", "WAITING");
		long id = create("{\"name\":\"Books\",\"kind\":\"ONE_FAMILY\",\"templateId\":" + freeText
				+ ",\"body\":\"Please collect {Child name}'s books today.\",\"audience\":{\"groups\":[\"ONE_FAMILY\"],"
				+ "\"filter\":{\"familyIds\":[" + punia + "]}}}");
		call(post(URL + "/" + id + "/send"), "{\"sendNow\":true}").andExpect(status().isOk())
			.andExpect(jsonPath("$.body").value("Please collect {Child name}'s books today."));
	}

	@Test
	void nobodyToSendTo() throws Exception {
		long id = create(draft("NOTICE", noticeTemplate, "{\"groups\":[\"CURRENT_PARENTS\"],"
				+ "\"filter\":{\"classes\":[\"1\"]}}", ""));
		call(post(URL + "/" + id + "/send"), "{\"sendNow\":true}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("NO_RECIPIENTS"));
	}

	// ---------------------------------------------------------------- test and cancel

	@Test
	void testSendGoesToCallerOnlyAndIsNotCounted() throws Exception {
		long id = noticeDraft();
		call(post(URL + "/" + id + "/test"), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value("+91XXXXXX0001"))
			.andExpect(jsonPath("$.body").value("Aryan की PTM 10 अक्टूबर को है।"));
		Map<String, Object> row = jdbc.queryForMap("select purpose, phone, status, notification_id from message_outbox");
		assertThat(row).containsEntry("purpose", "TEST")
			.containsEntry("phone", "+919812340001")
			.containsEntry("status", "QUEUED");
		// Still a draft; the test copy is not one of the recipients.
		call(get(URL + "/" + id), null).andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.counts.total").value(3));
	}

	@Test
	void cancelScheduledWritesNothing() throws Exception {
		long id = noticeDraft();
		call(post(URL + "/" + id + "/send"), "{\"scheduledAt\":\"2026-10-10T10:00:00+05:30\"}");
		call(delete(URL + "/" + id), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.note").value("Cancelled before sending."));
		assertThat(outboxRows()).isZero();
		call(delete(URL + "/" + id), null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("NOTIFICATION_NOT_EDITABLE"));
	}

	@Test
	void cancelSendingCancelsOnlyQueuedRows() throws Exception {
		long id = noticeDraft();
		call(post(URL + "/" + id + "/send"), "{\"sendNow\":true}");
		// As if the dispatcher wrote 3 rows and the worker sent one of them.
		Timestamp now = Timestamp.from(Instant.parse("2026-10-07T04:30:00Z"));
		for (String[] r : new String[][] { { "+919811100001", "SENT" }, { "+919811100002", "QUEUED" },
				{ "+919811100004", "QUEUED" } }) {
			jdbc.update("insert into message_outbox (purpose, channel, phone, body, status, notification_id, "
					+ "next_try_at) values ('NOTICE', 'WHATSAPP', ?, 'x', ?, ?, ?)", r[0], r[1], id, now);
		}
		call(delete(URL + "/" + id), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.note").value("Cancelled while sending: 1 sent, 2 not sent."));
		assertThat(jdbc.queryForList("select status from message_outbox order by phone", String.class))
			.containsExactly("SENT", "CANCELLED", "CANCELLED");
	}

	// ---------------------------------------------------------------- read

	@Test
	void recipientsBeforeAndAfterSending() throws Exception {
		long id = noticeDraft();
		call(get(URL + "/" + id + "/recipients?size=2"), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(3))
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].phone").value("+91XXXXXX0001"))
			.andExpect(jsonPath("$.items[0].childName").value("Aryan और Siya"))
			.andExpect(jsonPath("$.items[0].status").doesNotExist());
		call(post(URL + "/" + id + "/send"), "{\"sendNow\":true}");
		jdbc.update("insert into message_outbox (purpose, channel, phone, body, status, skip_reason, notification_id, "
				+ "guardian_id, template_values) values ('NOTICE', 'WHATSAPP', '+919811100004', 'x', 'SKIPPED', "
				+ "'NO_PHONE', ?, ?, '{\"Name\": \"Anil Sharma\"}'::jsonb)", id, sharma);
		call(get(URL + "/" + id + "/recipients"), null).andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].name").value("Anil Sharma"))
			.andExpect(jsonPath("$.items[0].status").value("SKIPPED"))
			.andExpect(jsonPath("$.items[0].skipReason").value("NO_PHONE"));
	}

	@Test
	void listWithFilters() throws Exception {
		long sent = noticeDraft();
		noticeDraft();
		call(post(URL + "/" + sent + "/send"), "{\"sendNow\":true}");
		call(get(URL), null).andExpect(jsonPath("$.totalItems").value(2))
			.andExpect(jsonPath("$.items[0].counts").doesNotExist());
		call(get(URL + "?status=SENDING"), null).andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].id").value(sent));
		call(get(URL + "?kind=MARKETING"), null).andExpect(jsonPath("$.totalItems").value(0));
		call(get(URL + "?from=2026-10-08"), null).andExpect(jsonPath("$.totalItems").value(0));
		call(get(URL + "?size=500"), null).andExpect(status().isBadRequest());
	}

}

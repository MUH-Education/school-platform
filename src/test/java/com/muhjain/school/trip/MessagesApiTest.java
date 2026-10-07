package com.muhjain.school.trip;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Task 5.8: the Messages API. Story: Aryan has two parent phones, Siya one. All three SMS are queued today. */
class MessagesApiTest extends TripTestBase {

	// The 8 templates live in the database, which all test classes share. Some tests change them, so put them back.
	private List<Map<String, Object>> originalTemplates;

	@BeforeEach
	void rememberTemplates() {
		originalTemplates = jdbc.queryForList("select code, body, provider_template_id, active from message_template");
	}

	@AfterEach
	void restoreTemplates() {
		for (Map<String, Object> t : originalTemplates) {
			jdbc.update("update message_template set body = ?, provider_template_id = ?, active = ? where code = ?",
					t.get("body"), t.get("provider_template_id"), t.get("active"), t.get("code"));
		}
	}

	@BeforeEach
	void queueMessages() throws Exception {
		parent(aryan, "+919811100001");
		parent(aryan, "+919811100002");
		parent(siya, "+919811104321");
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10"), morning(siya, "DONE", "07:43:00"));
	}

	private void parent(long student, String phone) {
		long guardian = jdbc.queryForObject("insert into guardian (name, phone) values ('P', ?) returning id",
				Long.class, phone);
		jdbc.update("insert into student_guardian (student_id, guardian_id, relation) values (?, ?, 'OTHER')", student,
				guardian);
	}

	@Test
	void phonesAreMaskedInTheMessagesList() throws Exception {
		getAs(officeToken, "/api/v1/messages").andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(3))
			.andExpect(jsonPath("$.items[0].phone").value("+91XXXXXX4321"))
			.andExpect(jsonPath("$.items[0].studentName").value("Siya"))
			.andExpect(jsonPath("$.items[0].status").value("QUEUED"))
			.andExpect(jsonPath("$.items[0].eventType").value("BOARDED_MORNING"))
			.andExpect(jsonPath("$.items[0].body").value("Siya सुबह की बस में चढ़ गई — 7:43। MUH Jain School"))
			.andExpect(content().string(not(containsString("+919811"))));
	}

	@Test
	void filtersByStudentStatusAndPhonePart() throws Exception {
		getAs(officeToken, "/api/v1/messages?studentId=" + aryan).andExpect(jsonPath("$.totalItems").value(2));
		getAs(officeToken, "/api/v1/messages?status=FAILED").andExpect(jsonPath("$.totalItems").value(0));
		getAs(officeToken, "/api/v1/messages?status=QUEUED").andExpect(jsonPath("$.totalItems").value(3));
		getAs(officeToken, "/api/v1/messages?phone=4321").andExpect(jsonPath("$.totalItems").value(1));
		getAs(officeToken, "/api/v1/messages?phone=43").andExpect(status().isBadRequest());
		getAs(officeToken, "/api/v1/messages?date=2026-10-06").andExpect(jsonPath("$.totalItems").value(0));
		getAs(officeToken, "/api/v1/messages?size=2").andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void summaryCountsTheDay() throws Exception {
		getAs(officeToken, "/api/v1/messages/summary").andExpect(status().isOk())
			.andExpect(jsonPath("$.queued").value(3))
			.andExpect(jsonPath("$.sent").value(0))
			.andExpect(jsonPath("$.failed").value(0))
			.andExpect(jsonPath("$.total").value(3));
		jdbc.update("update message_outbox set status = 'FAILED', error = 'invalid number' where phone = "
				+ "'+919811100001'");
		getAs(officeToken, "/api/v1/messages/summary?date=" + TODAY).andExpect(jsonPath("$.queued").value(2))
			.andExpect(jsonPath("$.failed").value(1));
		getAs(officeToken, "/api/v1/messages?status=FAILED").andExpect(jsonPath("$.items[0].error")
			.value("invalid number"));
	}

	@Test
	void templatesAreListedWithTheEightTexts() throws Exception {
		getAs(officeToken, "/api/v1/message-templates").andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(8))
			.andExpect(jsonPath("$[0].code").value("BOARDED_EVENING_F"));
	}

	private org.springframework.test.web.servlet.ResultActions putTemplate(String token, String code, String json)
			throws Exception {
		return mockMvc.perform(put("/api/v1/message-templates/" + code).header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	@Test
	void ownerCanSaveTheProviderIdAndTheTextAndOfficeCannot() throws Exception {
		String owner = tokenFor(addUser("+919812340001", com.muhjain.school.user.Role.OWNER));
		String body = "{name} स्कूल पहुँच गया — {time}। MUH Jain School";
		putTemplate(owner, "REACHED_SCHOOL_M", "{\"body\":\"" + body + "\",\"providerTemplateId\":\"1107001\","
				+ "\"active\":true}").andExpect(status().isOk())
			.andExpect(jsonPath("$.providerTemplateId").value("1107001"));
		putTemplate(officeToken, "REACHED_SCHOOL_M", "{\"body\":\"x {name} {time}\",\"active\":true}")
			.andExpect(status().isForbidden());
	}

	@Test
	void templateTextMustKeepPlaceholdersAndFitIn70Characters() throws Exception {
		String owner = tokenFor(addUser("+919812340001", com.muhjain.school.user.Role.OWNER));
		putTemplate(owner, "REACHED_SCHOOL_M", "{\"body\":\"no placeholders\",\"active\":true}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.body").exists());
		putTemplate(owner, "REACHED_SCHOOL_M", "{\"body\":\"{name} {time} " + "x".repeat(70) + "\",\"active\":true}")
			.andExpect(status().isBadRequest());
		putTemplate(owner, "NO_SUCH", "{\"body\":\"{name} {time}\",\"active\":true}").andExpect(status().isNotFound());
	}

	@Test
	void inactiveTemplateQueuesNoSms() throws Exception {
		jdbc.update("update message_template set active = false where code = 'BOARDED_MORNING_M'");
		sendMarks(balwanToken, morning(dev, "DONE", "07:44:00"));
		getAs(officeToken, "/api/v1/messages?studentId=" + dev).andExpect(jsonPath("$.totalItems").value(0));
	}

	@Test
	void noTokenGives401AndAttendantGets403() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/messages")).andExpect(status().isUnauthorized());
		getAs(balwanToken, "/api/v1/messages").andExpect(status().isForbidden());
		getAs(balwanToken, "/api/v1/messages/summary").andExpect(status().isForbidden());
		getAs(balwanToken, "/api/v1/message-templates").andExpect(status().isForbidden());
	}

}

package com.muhjain.school.enquiry;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Task 6.10: create, status change and follow-up are in the change history. */
class EnquiryAuditTest extends EnquiryTestBase {

	private List<String> lines() {
		return jdbc.queryForList("select summary from audit_log where entity_type = 'ENQUIRY' order by id",
				String.class);
	}

	@Test
	void createStatusAndFollowUpAreInTheChangeHistory() throws Exception {
		long id = idOf(create(desk, FIVE));
		move(desk, id, "{\"status\":\"VISITED\"}");
		followUp(desk, id, "{\"note\":\"Called. Will visit on Sunday.\",\"nextActionOn\":\"2026-10-12\"}");
		followUp(desk, id, "{\"note\":\"Final call\"}");
		move(desk, id, "{\"status\":\"LOST\",\"lostReason\":\"Joined a school near home\"}");
		assertThat(lines()).containsExactly("Enquiry added: Ramesh Jain, Jakhal, class 3 (WALK_IN)",
				"Status changed from NEW to VISITED",
				"Follow-up added: Called. Will visit on Sunday., next 12 Oct 2026",
				"Follow-up added: Final call, no next date",
				"Status changed from VISITED to LOST: Joined a school near home");
		assertThat(jdbc.queryForObject("select changed_by from audit_log where entity_type = 'ENQUIRY' limit 1",
				Long.class)).isNotNull();
	}

	@Test
	void noFullPhoneIsWrittenAndRefusedChangesWriteNothing() throws Exception {
		long id = idOf(create(desk, FIVE));
		move(desk, id, "{\"status\":\"ADMITTED\"}");
		move(desk, id, "{\"status\":\"LOST\"}");
		move(desk, id, "{\"status\":\"NEW\"}"); // unchanged
		assertThat(lines()).hasSize(1);
		assertThat(jdbc.queryForObject("select details::text from audit_log where entity_type = 'ENQUIRY'",
				String.class)).contains("+91XXXXXX0208").doesNotContain("9812340208");
	}

	@Test
	void admissionIsInTheHistoryToo() throws Exception {
		long id = idOf(create(desk, FIVE));
		mockMvcAdmit(id);
		assertThat(lines()).last().asString().startsWith("Admitted");
	}

	private void mockMvcAdmit(long enquiryId) throws Exception {
		String body = "{\"name\":\"Aryan Jain\",\"dob\":\"2018-05-14\",\"gender\":\"M\",\"className\":\"3\","
				+ "\"village\":\"Jakhal\",\"fatherOccupation\":\"OTHER\",\"guardians\":[{\"name\":\"Ramesh\","
				+ "\"phone\":\"9812340208\",\"relation\":\"FATHER\"}],\"enquiryId\":" + enquiryId + "}";
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admissions")
			.header("Authorization", bearer(desk))
			.contentType("application/json")
			.content(body)).andReturn();
	}

}

package com.muhjain.school.enquiry;

import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Story for the Phase 6 tests. Today is 7 Oct 2026, 10:00 school time. Neelam (admissions desk) types the enquiries.
 */
abstract class EnquiryTestBase extends AbstractIntegrationTest {

	static final String FIVE = "{\"parentName\":\"Ramesh Jain\",\"phone\":\"98123 40208\",\"village\":\"Jakhal\","
			+ "\"classSought\":\"3\",\"source\":\"WALK_IN\"}";

	String desk;

	String office;

	String owner;

	@BeforeEach
	void addPeople() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		desk = tokenFor(addUser("+919812340004", Role.ADMISSIONS_DESK));
		office = tokenFor(addUser("+919812340002", Role.OFFICE_ADMIN));
		owner = tokenFor(addUser("+919812340001", Role.OWNER));
	}

	static String json(String phone, String cls, String extra) {
		return "{\"parentName\":\"Parent " + phone + "\",\"phone\":\"" + phone + "\",\"village\":\"Jakhal\","
				+ "\"classSought\":\"" + cls + "\",\"source\":\"WALK_IN\"" + ((extra == null) ? "" : "," + extra) + "}";
	}

	ResultActions create(String token, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/enquiries").header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	ResultActions change(String token, long id, String body) throws Exception {
		return mockMvc.perform(put("/api/v1/enquiries/" + id).header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	ResultActions read(String token, String url) throws Exception {
		return mockMvc.perform(get(url).header("Authorization", bearer(token)));
	}

	ResultActions move(String token, long id, String json) throws Exception {
		return mockMvc.perform(post("/api/v1/enquiries/" + id + "/status").header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	ResultActions followUp(String token, long id, String json) throws Exception {
		return mockMvc.perform(post("/api/v1/enquiries/" + id + "/follow-ups").header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	/** Saves an enquiry straight into the database and returns its id. */
	long addEnquiry(String phone, String cls, String status, String village, String nextFollowUp) {
		return jdbc.queryForObject("insert into enquiry (parent_name, phone, village, class_sought, source, status, "
				+ "next_follow_up_on, session_name, lost_reason) values ('Parent', ?, ?, ?, 'WALK_IN', ?, ?::date, "
				+ "'2027-28', case when ? = 'LOST' then 'far away' end) returning id", Long.class, phone, village, cls,
				status, nextFollowUp, status);
	}

	long idOf(org.springframework.test.web.servlet.ResultActions result) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString();
		return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
	}

}

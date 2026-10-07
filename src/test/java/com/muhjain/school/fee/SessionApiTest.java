package com.muhjain.school.fee;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rules 1 and 2 of phase 7: one current session, and the standard fee of each class. */
class SessionApiTest extends FeeTestBase {

	private static final String NEXT = "{\"name\":\"2027-28\",\"startsOn\":\"2027-04-01\",\"endsOn\":\"2028-03-31\","
			+ "\"current\":%s}";

	@Test
	void listShowsTheCurrentSession() throws Exception {
		get(transport, "/api/v1/sessions").andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].name").value("2026-27"))
			.andExpect(jsonPath("$[0].current").value(true));
	}

	@Test
	void ownerAddsASessionAndOldOneStaysCurrent() throws Exception {
		post(owner, "/api/v1/sessions", NEXT.formatted(false)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.current").value(false));
		get(owner, "/api/v1/sessions").andExpect(jsonPath("$[?(@.current==true)].name").value("2026-27"));
	}

	@Test
	void makingANewSessionCurrentRetiresTheOldOne() throws Exception {
		post(owner, "/api/v1/sessions", NEXT.formatted(true)).andExpect(status().isCreated());
		get(owner, "/api/v1/sessions").andExpect(jsonPath("$[?(@.current==true)]", hasSize(1)))
			.andExpect(jsonPath("$[?(@.current==true)].name").value("2027-28"));
	}

	@Test
	void wrongNameDuplicateAndOverlapAreRefused() throws Exception {
		post(owner, "/api/v1/sessions", NEXT.formatted(false).replace("2027-28", "2027-29"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.name").exists());
		post(owner, "/api/v1/sessions",
				"{\"name\":\"2026-27\",\"startsOn\":\"2026-04-01\",\"endsOn\":\"2027-03-31\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("SESSION_ALREADY_EXISTS"));
		post(owner, "/api/v1/sessions",
				"{\"name\":\"2026-27\",\"startsOn\":\"2026-12-01\",\"endsOn\":\"2027-11-30\"}")
			.andExpect(status().isConflict());
	}

	@Test
	void classFeesAreSavedAsOneListAndReadInSchoolOrder() throws Exception {
		long session = currentSessionId();
		String url = "/api/v1/sessions/" + session + "/class-fees";
		put(owner, url, "{\"fees\":[{\"className\":\"3\",\"schoolFee\":30000},{\"className\":\"lkg\","
				+ "\"schoolFee\":18000.50}]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].className").value("LKG"))
			.andExpect(jsonPath("$[1].className").value("3"))
			.andExpect(jsonPath("$[1].schoolFee").value(30000.00));
		// A new list replaces the old one: class 3 loses its fee.
		put(owner, url, "{\"fees\":[{\"className\":\"4\",\"schoolFee\":31000}]}").andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)));
		get(desk, url).andExpect(status().isOk()).andExpect(jsonPath("$[0].className").value("4"));
	}

	@Test
	void badClassFeesAreRefusedAndNothingChanges() throws Exception {
		long session = currentSessionId();
		String url = "/api/v1/sessions/" + session + "/class-fees";
		put(owner, url, "{\"fees\":[{\"className\":\"3\",\"schoolFee\":30000}]}").andExpect(status().isOk());
		put(owner, url, "{\"fees\":[{\"className\":\"13\",\"schoolFee\":1}]}").andExpect(status().isBadRequest());
		put(owner, url, "{\"fees\":[{\"className\":\"3\",\"schoolFee\":-1}]}").andExpect(status().isBadRequest());
		put(owner, url, "{\"fees\":[{\"className\":\"3\",\"schoolFee\":1},{\"className\":\"3\",\"schoolFee\":2}]}")
			.andExpect(status().isBadRequest());
		get(owner, url).andExpect(jsonPath("$[0].schoolFee").value(30000.00));
		put(owner, "/api/v1/sessions/99999/class-fees", "{\"fees\":[]}").andExpect(status().isNotFound());
	}

	@Test
	void onlyOwnerChangesClassFees() throws Exception {
		String url = "/api/v1/sessions/" + currentSessionId() + "/class-fees";
		put(office, url, "{\"fees\":[]}").andExpect(status().isForbidden());
		get(transport, url).andExpect(status().isForbidden());
	}

}

package com.muhjain.school.trip;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rules 11 to 13 and the attendant scope for reading. */
class ManifestApiTest extends TripTestBase {

	@Test
	void attendantSeesOwnRouteWithoutSendingRouteId() throws Exception {
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10"));
		getAs(balwanToken, "/api/v1/trips/manifest").andExpect(status().isOk())
			.andExpect(jsonPath("$.routeName").value("Route 4"))
			.andExpect(jsonPath("$.stops.length()").value(2))
			.andExpect(jsonPath("$.stops[0].name").value("Jakhal"))
			.andExpect(jsonPath("$.stops[0].morningTime").value("07:40:00"))
			.andExpect(jsonPath("$.stops[0].children.length()").value(2))
			.andExpect(jsonPath("$.stops[0].children[0].name").value("Aryan"))
			.andExpect(jsonPath("$.stops[0].children[0].events.boardedMorning.outcome").value("DONE"))
			.andExpect(jsonPath("$.stops[0].children[0].events.boardedMorning.occurredAt")
				.value("2026-10-07T07:42:10+05:30"))
			.andExpect(jsonPath("$.stops[0].children[0].events.reachedSchool").doesNotExist())
			.andExpect(jsonPath("$.stops[1].children[0].name").value("Meera"));
	}

	@Test
	void manifestHasNoPhoneNumbers() throws Exception {
		getAs(balwanToken, "/api/v1/trips/manifest?routeId=" + route4).andExpect(status().isOk())
			.andExpect(content().string(not(containsString("phone"))))
			.andExpect(content().string(not(containsString("+91"))));
	}

	@Test
	void attendantCannotOpenAnotherRoute() throws Exception {
		getAs(balwanToken, "/api/v1/trips/manifest?routeId=" + route7).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("NOT_YOUR_ROUTE"));
	}

	@Test
	void attendantCannotReadAnotherDay() throws Exception {
		getAs(balwanToken, "/api/v1/trips/manifest?date=2026-10-06").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("DATE_NOT_ALLOWED"));
	}

	@Test
	void attendantWithNoRouteGets403() throws Exception {
		jdbc.update("delete from vehicle_assignment where staff_id = ?", balwan.getStaffId());
		getAs(balwanToken, "/api/v1/trips/manifest").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("NOT_YOUR_ROUTE"));
	}

	@Test
	void officeNeedsRouteIdButMayReadAnyRouteAndDate() throws Exception {
		getAs(officeToken, "/api/v1/trips/manifest").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"));
		getAs(officeToken, "/api/v1/trips/manifest?routeId=" + route7 + "&date=2026-10-01").andExpect(status().isOk())
			.andExpect(jsonPath("$.routeName").value("Route 7"));
		getAs(officeToken, "/api/v1/trips/manifest?routeId=999999").andExpect(status().isNotFound());
	}

	@Test
	void childWhoseBusStartsTomorrowIsNotInTodaysManifest() throws Exception {
		addChild("Ishaan", "M", route4, jakhal, "2026-10-08");
		getAs(balwanToken, "/api/v1/trips/manifest").andExpect(status().isOk())
			.andExpect(jsonPath("$.stops[0].children.length()").value(2));
		getAs(officeToken, "/api/v1/trips/manifest?routeId=" + route4 + "&date=2026-10-08").andExpect(status().isOk())
			.andExpect(jsonPath("$.stops[0].children.length()").value(3));
	}

	@Test
	void noTokenGives401AndWrongRoleGives403() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/trips/manifest"))
			.andExpect(status().isUnauthorized());
		String desk = tokenFor(addUser("+919812340077", com.muhjain.school.user.Role.ADMISSIONS_DESK));
		getAs(desk, "/api/v1/trips/manifest?routeId=" + route4).andExpect(status().isForbidden());
	}

}

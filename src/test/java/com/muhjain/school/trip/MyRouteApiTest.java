package com.muhjain.school.trip;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /trips/my-route} and the {@code route} of {@code GET /auth/me}. */
class MyRouteApiTest extends TripTestBase {

	@Test
	void showsRouteAndProgressOfTheFourJobs() throws Exception {
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10"), morning(siya, "ABSENT", "07:43:00"),
				mark(aryan, "BOARDED_EVENING", "NOT_TRAVELLING", TODAY, TODAY + "T07:44:00+05:30"));
		getAs(balwanToken, "/api/v1/trips/my-route").andExpect(status().isOk())
			.andExpect(jsonPath("$.route.name").value("Route 4"))
			.andExpect(jsonPath("$.route.vehicle").value("Van 4"))
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.jobs.boardedMorning.done").value(1))
			.andExpect(jsonPath("$.jobs.boardedMorning.absent").value(1))
			.andExpect(jsonPath("$.jobs.boardedMorning.remaining").value(1))
			.andExpect(jsonPath("$.jobs.boardedEvening.notTravelling").value(1))
			.andExpect(jsonPath("$.jobs.reachedSchool.remaining").value(3));
	}

	@Test
	void attendantWithNoVehicleTodayGetsEmptyRoute() throws Exception {
		jdbc.update("delete from vehicle_assignment where staff_id = ?", balwan.getStaffId());
		getAs(balwanToken, "/api/v1/trips/my-route").andExpect(status().isOk())
			.andExpect(jsonPath("$.route").doesNotExist())
			.andExpect(jsonPath("$.jobs").doesNotExist());
		getAs(balwanToken, "/api/v1/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.route").doesNotExist());
	}

	@Test
	void replacementAttendantSeesTheRouteOnlyOnHisDays() throws Exception {
		// Naresh is a spare attendant. He covers Van 4 only today. Balwan is replaced today and is back tomorrow.
		var naresh = addUser("+919812340013", com.muhjain.school.user.Role.ATTENDANT);
		String nareshToken = tokenFor(naresh);
		long van4 = jdbc.queryForObject("select vehicle_id from route where id = ?", Long.class, route4);
		addAssignment(van4, naresh.getStaffId(), "ATTENDANT", TODAY, TODAY, true);
		getAs(nareshToken, "/api/v1/trips/my-route").andExpect(jsonPath("$.route.name").value("Route 4"));
		getAs(balwanToken, "/api/v1/trips/my-route").andExpect(jsonPath("$.route").doesNotExist());
		// The replacement can tap Route 4 children today, and Balwan cannot.
		sendMarks(nareshToken, morning(aryan, "DONE", "07:42:00")).andExpect(jsonPath("$.results[0].ok").value(true));
		sendMarks(balwanToken, morning(siya, "DONE", "07:42:00"))
			.andExpect(jsonPath("$.results[0].error").value("NOT_YOUR_ROUTE"));
		clock.advance(java.time.Duration.ofDays(1));
		getAs(nareshToken, "/api/v1/trips/my-route").andExpect(jsonPath("$.route").doesNotExist());
		getAs(balwanToken, "/api/v1/trips/my-route").andExpect(jsonPath("$.route.name").value("Route 4"));
	}

	@Test
	void meShowsTheRouteOfTheAttendantAndNullForOffice() throws Exception {
		getAs(balwanToken, "/api/v1/auth/me").andExpect(jsonPath("$.route.id").value(route4))
			.andExpect(jsonPath("$.route.name").value("Route 4"))
			.andExpect(jsonPath("$.route.vehicle").value("Van 4"));
		getAs(officeToken, "/api/v1/auth/me").andExpect(jsonPath("$.route").doesNotExist());
	}

	@Test
	void noTokenGives401AndOfficeWithoutTripsRecordGives403() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/trips/my-route"))
			.andExpect(status().isUnauthorized());
		getAs(officeToken, "/api/v1/trips/my-route").andExpect(status().isForbidden());
	}

}

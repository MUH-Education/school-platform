package com.muhjain.school.trip;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The attention list. Story: Aryan, Siya and Meera ride Route 4. In the morning Aryan and Siya came, Meera was
 * absent. At 14:50 the evening boarding has started.
 */
class AttentionApiTest extends TripTestBase {

	private static final String EVENING = "BOARDED_EVENING";

	private void morningRoutine() throws Exception {
		sendMarks(balwanToken, morning(aryan, "DONE", "07:30:00"), morning(siya, "DONE", "07:31:00"),
				morning(meera, "ABSENT", "07:50:00"));
	}

	private String evening(long student, String outcome, String time) {
		return mark(student, EVENING, outcome, TODAY, TODAY + "T" + time + "+05:30");
	}

	@Test
	void childWhoCameInMorningWithNoEveningTapIsMissing() throws Exception {
		morningRoutine();
		clock.advance(Duration.ofHours(7).plusMinutes(2)); // 14:50
		sendMarks(balwanToken, evening(aryan, "DONE", "14:40:00"));
		getAs(officeToken, "/api/v1/bus-status/attention").andExpect(status().isOk())
			.andExpect(jsonPath("$.missingInEvening.length()").value(1))
			.andExpect(jsonPath("$.missingInEvening[0].name").value("Siya"))
			.andExpect(jsonPath("$.missingInEvening[0].className").value("3"))
			.andExpect(jsonPath("$.missingInEvening[0].stopName").value("Jakhal"))
			.andExpect(jsonPath("$.missingInEvening[0].routeName").value("Route 4"))
			.andExpect(jsonPath("$.missingInEvening[0].eveningBoardingStartedAt").value("14:40"));
	}

	@Test
	void childMarkedNotTravellingIsNotMissing() throws Exception {
		morningRoutine();
		clock.advance(Duration.ofHours(7).plusMinutes(2));
		sendMarks(balwanToken, evening(aryan, "DONE", "14:40:00"), evening(siya, "NOT_TRAVELLING", "14:41:00"));
		getAs(officeToken, "/api/v1/bus-status/attention").andExpect(jsonPath("$.missingInEvening.length()").value(0));
	}

	@Test
	void childMarkedAbsentInTheEveningIsNotMissing() throws Exception {
		morningRoutine();
		clock.advance(Duration.ofHours(7).plusMinutes(2));
		sendMarks(balwanToken, evening(aryan, "DONE", "14:40:00"), evening(siya, "ABSENT", "14:41:00"));
		getAs(officeToken, "/api/v1/bus-status/attention").andExpect(jsonPath("$.missingInEvening.length()").value(0));
	}

	@Test
	void childAbsentInTheMorningIsNotMissingInTheEvening() throws Exception {
		morningRoutine();
		clock.advance(Duration.ofHours(7).plusMinutes(2));
		sendMarks(balwanToken, evening(aryan, "DONE", "14:40:00"));
		// Meera was absent in the morning: she is not on the missing list, only Siya is.
		getAs(officeToken, "/api/v1/bus-status/attention")
			.andExpect(jsonPath("$.missingInEvening[?(@.name=='Meera')]").isEmpty());
	}

	@Test
	void nobodyIsMissingBeforeEveningBoardingStarts() throws Exception {
		morningRoutine();
		clock.advance(Duration.ofHours(7).plusMinutes(2));
		getAs(officeToken, "/api/v1/bus-status/attention").andExpect(jsonPath("$.missingInEvening.length()").value(0));
	}

	@Test
	void childWhoReachedSchoolButWasNotTappedOnBoardingIsStillExpectedInTheEvening() throws Exception {
		// Only the REACHED_SCHOOL tap exists for Meera: she came (DONE), so no evening tap means missing.
		sendMarks(balwanToken, mark(meera, "REACHED_SCHOOL", "DONE", TODAY, TODAY + "T08:05:00+05:30"),
				morning(aryan, "DONE", "07:30:00"));
		clock.advance(Duration.ofHours(7).plusMinutes(2));
		sendMarks(balwanToken, evening(aryan, "DONE", "14:40:00"));
		getAs(officeToken, "/api/v1/bus-status/attention").andExpect(jsonPath("$.missingInEvening.length()").value(1))
			.andExpect(jsonPath("$.missingInEvening[0].name").value("Meera"));
	}

	@Test
	void lateAndNoTapsRoutesAreListedInTheMorning() throws Exception {
		clock.advance(Duration.ofMinutes(5)); // 7:53, Route 4 first stop due 7:40 (13 minutes), Route 7 due 7:30
		getAs(officeToken, "/api/v1/bus-status/attention").andExpect(status().isOk())
			.andExpect(jsonPath("$.routes.length()").value(2))
			.andExpect(jsonPath("$.routes[?(@.name=='Route 7')].state").value("NO_TAPS"))
			.andExpect(jsonPath("$.routes[?(@.name=='Route 4')].lateMinutes").value(13));
	}

	@Test
	void morningWarningsAreNotShownInTheAfternoon() throws Exception {
		clock.advance(Duration.ofHours(7));
		getAs(officeToken, "/api/v1/bus-status/attention").andExpect(jsonPath("$.routes.length()").value(0));
	}

	@Test
	void noTokenGives401AndWrongRoleGives403() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/bus-status/attention"))
			.andExpect(status().isUnauthorized());
		getAs(balwanToken, "/api/v1/bus-status/attention").andExpect(status().isForbidden());
	}

}

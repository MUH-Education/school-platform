package com.muhjain.school.trip;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bus status at the fixed time 7:48 (rule 16 examples), seen through the URLs. */
class BusStatusApiTest extends TripTestBase {

	private long route3;

	private long route5;

	private long route9;

	private long route2;

	private void addFourRoutes() {
		route3 = busRoute("Route 3", "Van 3", "07:15");
		route5 = busRoute("Route 5", "Van 5", "07:24");
		route9 = busRoute("Route 9", "Van 9", "07:50");
		route2 = busRoute("Route 2", "Van 2", "07:20");
	}

	private long busRoute(String name, String van, String firstDue) {
		long route = addRoute(name, addVehicle(van));
		long stop = addStop(route, name + " first", 1, firstDue, "14:30");
		addStop(route, name + " second", 2, "08:10", "14:20");
		addChild(name + " child", "M", route, stop, "2026-04-01");
		return route;
	}

	private long firstChild(long route) {
		return jdbc.queryForObject("select student_id from transport_enrolment where route_id = ?", Long.class, route);
	}

	private void tap(long student, String event, String outcome, String time) {
		jdbc.update("insert into boarding_event (student_id, route_id, service_date, event_type, outcome, occurred_at, "
				+ "recorded_by) select ?, route_id, '2026-10-07', ?, ?, ?::timestamptz, ? from transport_enrolment "
				+ "where student_id = ?", student, event, outcome, "2026-10-07T" + time + "+05:30", office.getId(),
				student);
	}

	@Test
	void showsTheStatesOfTheDesignAt0748() throws Exception {
		addFourRoutes();
		tap(firstChild(route5), "BOARDED_MORNING", "DONE", "07:40:00");
		tap(firstChild(route2), "BOARDED_MORNING", "DONE", "07:25:00");
		tap(firstChild(route2), "REACHED_SCHOOL", "DONE", "07:46:00");
		getAs(officeToken, "/api/v1/bus-status").andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(6))
			.andExpect(jsonPath("$[?(@.name=='Route 3')].state").value("NO_TAPS"))
			.andExpect(jsonPath("$[?(@.name=='Route 3')].lateMinutes").value(33))
			.andExpect(jsonPath("$[?(@.name=='Route 5')].state").value("LATE"))
			.andExpect(jsonPath("$[?(@.name=='Route 5')].lateMinutes").value(16))
			.andExpect(jsonPath("$[?(@.name=='Route 9')].state").value("NOT_STARTED"))
			.andExpect(jsonPath("$[?(@.name=='Route 2')].state").value("REACHED_SCHOOL"))
			.andExpect(jsonPath("$[?(@.name=='Route 2')].reachedAt").value("07:46"))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].state").value("NOT_STARTED")); // due 07:40, only 8 minutes
	}

	@Test
	void routeEntryHasVehicleAttendantCountsAndStops() throws Exception {
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10"), morning(siya, "ABSENT", "07:43:00"));
		getAs(officeToken, "/api/v1/bus-status?phase=MORNING")
			.andExpect(jsonPath("$[?(@.name=='Route 4')].vehicle").value("Van 4"))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].attendant").value("Balwan"))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].phase").value("MORNING"))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].boarded").value(1))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].absent").value(1))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].total").value(3))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].state").value("ON_THE_WAY"))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].stops[0].name").value("Jakhal"))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].stops[0].due").value("07:40"))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].stops[0].tappedAt").value("07:42"))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].stops[0].state").value("DONE"))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].stops[1].state").value("NEXT"));
	}

	@Test
	void withoutPhaseItIsMorningBeforeNoonAndEveningAfter() throws Exception {
		getAs(officeToken, "/api/v1/bus-status").andExpect(jsonPath("$[0].phase").value("MORNING"));
		clock.advance(java.time.Duration.ofHours(5)); // 12:48
		getAs(officeToken, "/api/v1/bus-status").andExpect(jsonPath("$[0].phase").value("EVENING"));
		getAs(officeToken, "/api/v1/bus-status?phase=MORNING").andExpect(jsonPath("$[0].phase").value("MORNING"));
	}

	@Test
	void unknownPhaseIs400() throws Exception {
		getAs(officeToken, "/api/v1/bus-status?phase=NIGHT").andExpect(status().isBadRequest());
	}

	@Test
	void oneRouteShowsEveryChildWithFourEvents() throws Exception {
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10"));
		getAs(officeToken, "/api/v1/bus-status/routes/" + route4).andExpect(status().isOk())
			.andExpect(jsonPath("$.route.name").value("Route 4"))
			.andExpect(jsonPath("$.children.length()").value(3))
			.andExpect(jsonPath("$.children[?(@.name=='Aryan')].stopName").value("Jakhal"))
			.andExpect(jsonPath("$.children[?(@.name=='Aryan')].events.boardedMorning.outcome").value("DONE"))
			.andExpect(jsonPath("$.children[?(@.name=='Siya')].events.boardedMorning").value((Object) null));
		getAs(officeToken, "/api/v1/bus-status/routes/999999").andExpect(status().isNotFound());
	}

	@Test
	void eveningStatusFollowsBoardingAndHomeTaps() throws Exception {
		clock.advance(java.time.Duration.ofHours(7)); // 14:48
		getAs(officeToken, "/api/v1/bus-status?phase=EVENING")
			.andExpect(jsonPath("$[?(@.name=='Route 4')].state").value("NOT_STARTED"));
		tap(aryan, "BOARDED_EVENING", "DONE", "14:40:00");
		getAs(officeToken, "/api/v1/bus-status?phase=EVENING")
			.andExpect(jsonPath("$[?(@.name=='Route 4')].state").value("BOARDING"))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].boarded").value(1));
		tap(siya, "BOARDED_EVENING", "NOT_TRAVELLING", "14:41:00");
		tap(meera, "BOARDED_EVENING", "ABSENT", "14:41:30");
		getAs(officeToken, "/api/v1/bus-status?phase=EVENING")
			.andExpect(jsonPath("$[?(@.name=='Route 4')].state").value("ON_THE_WAY"))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].notTravelling").value(1))
			.andExpect(jsonPath("$[?(@.name=='Route 4')].stops[0].name").value("Kanheri"));
		tap(aryan, "REACHED_HOME", "DONE", "15:05:00");
		getAs(officeToken, "/api/v1/bus-status?phase=EVENING")
			.andExpect(jsonPath("$[?(@.name=='Route 4')].state").value("DONE"));
	}

	@Test
	void noTokenGives401AndWrongRoleGives403() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/bus-status"))
			.andExpect(status().isUnauthorized());
		getAs(balwanToken, "/api/v1/bus-status").andExpect(status().isForbidden());
		getAs(balwanToken, "/api/v1/bus-status/routes/" + route4).andExpect(status().isForbidden());
	}

}

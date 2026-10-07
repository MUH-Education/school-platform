package com.muhjain.school.route;

import java.time.LocalDate;
import java.util.Optional;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The attendant's route follows the assignment of the day. Phase 4 protects every trip URL with this.
 * Story: Balwan is the attendant of Van 4 from 1 Apr 2026. Van 4 runs Route 4. Hari replaces Balwan 12 to 16 Oct.
 */
class AttendantRouteServiceTest extends AbstractIntegrationTest {

	@Autowired
	private AttendantRouteService attendantRoutes;

	private long van4;

	private long van1;

	private long route4;

	private long route1;

	private AppUser balwan;

	private AppUser hari;

	@BeforeEach
	void addStory() {
		van4 = addVehicle("Van 4");
		van1 = addVehicle("Van 1");
		route4 = addRoute("Route 4", van4, true);
		route1 = addRoute("Route 1", van1, true);
		balwan = addUser("+919812340011", Role.ATTENDANT);
		hari = addUser("+919812340012", Role.ATTENDANT);
		addAssignment(van4, balwan.getStaffId(), "ATTENDANT", "2026-04-01", null, false);
	}

	private long addRoute(String name, Long vehicleId, boolean active) {
		return jdbc.queryForObject("insert into route (name, vehicle_id, active) values (?, ?, ?) returning id",
				Long.class, name, vehicleId, active);
	}

	private Optional<AttendantRoute> routeOf(AppUser user, String day) {
		return attendantRoutes.routeFor(user.getId(), LocalDate.parse(day));
	}

	@Test
	void attendantOnVan4TodayGetsRoute4() {
		AttendantRoute route = routeOf(balwan, "2026-10-07").orElseThrow();

		assertThat(route.routeId()).isEqualTo(route4);
		assertThat(route.routeName()).isEqualTo("Route 4");
		assertThat(route.vehicleId()).isEqualTo(van4);
		assertThat(route.vehicleName()).isEqualTo("Van 4");
	}

	@Test
	void attendantRouteFollowsTheAssignmentOfTheDay() {
		// Hari replaces Balwan 12 to 16 Oct (temporary).
		addAssignment(van4, hari.getStaffId(), "ATTENDANT", "2026-10-12", "2026-10-16", true);

		// Before: Balwan has the route, Hari has none.
		assertThat(routeOf(balwan, "2026-10-11")).map(AttendantRoute::routeId).contains(route4);
		assertThat(routeOf(hari, "2026-10-11")).isEmpty();
		// During: Hari has it. Balwan is replaced, so he has none.
		assertThat(routeOf(hari, "2026-10-12")).map(AttendantRoute::routeId).contains(route4);
		assertThat(routeOf(hari, "2026-10-16")).map(AttendantRoute::routeId).contains(route4);
		assertThat(routeOf(balwan, "2026-10-14")).isEmpty();
		// After: Balwan again.
		assertThat(routeOf(balwan, "2026-10-17")).map(AttendantRoute::routeId).contains(route4);
		assertThat(routeOf(hari, "2026-10-17")).isEmpty();
	}

	@Test
	void permanentChangeMovesTheRouteOnTheFirstDay() {
		// From 1 Nov Hari is the attendant. Balwan's row ends on 31 Oct.
		jdbc.update("update vehicle_assignment set to_date = '2026-10-31' where staff_id = ?", balwan.getStaffId());
		addAssignment(van4, hari.getStaffId(), "ATTENDANT", "2026-11-01", null, false);

		assertThat(routeOf(balwan, "2026-10-31")).isPresent();
		assertThat(routeOf(hari, "2026-10-31")).isEmpty();
		assertThat(routeOf(balwan, "2026-11-01")).isEmpty();
		assertThat(routeOf(hari, "2026-11-01")).isPresent();
	}

	@Test
	void notAssignedTodayGivesNoRoute() {
		// Before the first day of the assignment.
		assertThat(routeOf(balwan, "2026-03-31")).isEmpty();
		// Hari has no assignment at all.
		assertThat(routeOf(hari, "2026-10-07")).isEmpty();
		// Balwan's assignment ended yesterday.
		jdbc.update("update vehicle_assignment set to_date = '2026-10-06' where staff_id = ?", balwan.getStaffId());
		assertThat(routeOf(balwan, "2026-10-07")).isEmpty();
		assertThat(routeOf(balwan, "2026-10-06")).isPresent();
	}

	@Test
	void routeChangesWhenTheAttendantMovesToAnotherVehicle() {
		jdbc.update("update vehicle_assignment set to_date = '2026-10-31' where staff_id = ?", balwan.getStaffId());
		addAssignment(van1, balwan.getStaffId(), "ATTENDANT", "2026-11-01", null, false);

		assertThat(routeOf(balwan, "2026-10-31")).map(AttendantRoute::routeName).contains("Route 4");
		assertThat(routeOf(balwan, "2026-11-01")).map(AttendantRoute::routeName).contains("Route 1");
		assertThat(routeOf(balwan, "2026-11-01")).map(AttendantRoute::routeId).contains(route1);
	}

	@Test
	void vehicleWithoutAnActiveRouteGivesNoRoute() {
		jdbc.update("update route set active = false where id = ?", route4);

		assertThat(routeOf(balwan, "2026-10-07")).isEmpty();

		// A new active route on the same vehicle is the one that counts.
		long route40 = addRoute("Route 40", van4, true);
		assertThat(routeOf(balwan, "2026-10-07")).map(AttendantRoute::routeId).contains(route40);
	}

	@Test
	void vehicleWithNoRouteAtAllGivesNoRoute() {
		jdbc.update("delete from route where id = ?", route4);

		assertThat(routeOf(balwan, "2026-10-07")).isEmpty();
	}

	@Test
	void onlyTheAttendantDutyCounts() {
		// Hari is on Van 1 as a helper (a wrong row, put in by hand). A helper is not an attendant.
		addAssignment(van1, hari.getStaffId(), "HELPER", "2026-04-01", null, false);

		assertThat(routeOf(hari, "2026-10-07")).isEmpty();
	}

	@Test
	void usersWithoutAStaffRowTurnedOffOrUnknownHaveNoRoute() {
		AppUser admin = addUser("+919812340002", Role.OFFICE_ADMIN);
		AppUser incharge = addUser("+919812340003", Role.TRANSPORT_INCHARGE);

		assertThat(attendantRoutes.routeFor(admin.getId(), LocalDate.of(2026, 10, 7))).isEmpty();
		assertThat(attendantRoutes.routeFor(incharge.getId(), LocalDate.of(2026, 10, 7))).isEmpty();
		assertThat(attendantRoutes.routeFor(999999L, LocalDate.of(2026, 10, 7))).isEmpty();

		balwan.setActive(false);
		userRepository.saveAndFlush(balwan);
		assertThat(routeOf(balwan, "2026-10-07")).isEmpty();
	}

	@Test
	void staffWhoIsTurnedOffHasNoRoute() {
		jdbc.update("update staff set active = false where id = ?", balwan.getStaffId());

		assertThat(routeOf(balwan, "2026-10-07")).isEmpty();
	}

	@Test
	void oneAttendantNeverGetsTheRouteOfAnother() {
		// Both are attendants of different vehicles. Each sees only their own route.
		addAssignment(van1, hari.getStaffId(), "ATTENDANT", "2026-04-01", null, false);

		assertThat(routeOf(balwan, "2026-10-07")).map(AttendantRoute::routeName).contains("Route 4");
		assertThat(routeOf(hari, "2026-10-07")).map(AttendantRoute::routeName).contains("Route 1");
	}

	@Test
	void changeMadeByTheOfficeIsSeenAtOnce() throws Exception {
		String token = tokenFor(addUser("+919812340003", Role.TRANSPORT_INCHARGE));

		mockMvc.perform(post("/api/v1/vehicles/" + van4 + "/assignments").header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"duty\":\"ATTENDANT\",\"staffId\":" + hari.getStaffId() + ",\"fromDate\":\"2026-10-12\","
					+ "\"toDate\":\"2026-10-16\",\"temporary\":true,\"reason\":\"ON_LEAVE\"}"))
			.andExpect(status().isCreated());

		assertThat(routeOf(hari, "2026-10-13")).map(AttendantRoute::routeId).contains(route4);
		assertThat(routeOf(balwan, "2026-10-13")).isEmpty();
		assertThat(routeOf(balwan, "2026-10-17")).map(AttendantRoute::routeId).contains(route4);
	}

}

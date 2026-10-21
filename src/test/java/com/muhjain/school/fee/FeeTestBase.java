package com.muhjain.school.fee;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;


/**
 * Story for the Phase 7 tests. Today is 7 Oct 2026, 10:00 school time. Neelam (office admin) types the fees,
 * Priya (admissions desk) can also, the owner (Rishabh) is the only one who can correct a payment.
 */
abstract class FeeTestBase extends AbstractIntegrationTest {

	private AppUser officeUser;

	private AppUser deskUser;

	private AppUser ownerUser;

	private AppUser transportUser;

	String office;

	String desk;

	String owner;

	String transport;

	private int guardianSeq;

	@BeforeEach
	void addPeople() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		officeUser = addUser("+919812340002", Role.OFFICE_ADMIN);
		deskUser = addUser("+919812340004", Role.ADMISSIONS_DESK);
		ownerUser = addUser("+919812340001", Role.OWNER);
		transportUser = addUser("+919812340003", Role.TRANSPORT_INCHARGE);
		refreshTokens();
	}

	/**
	 * A session's idle timeout is 15 minutes and its hard cap is 12 hours (docs/08-decisions.md). A test that
	 * jumps the clock forward with no real request in between would not survive that as a real user — call
	 * this after such a jump to log back in, the same way a real office user would if they had been away
	 * that long.
	 */
	void refreshTokens() {
		office = tokenFor(officeUser);
		desk = tokenFor(deskUser);
		owner = tokenFor(ownerUser);
		transport = tokenFor(transportUser);
	}

	// FeeTestBase fixes "now" to 7 Oct 2026, so the current session is the one whose dates include it.
	long currentSessionId() {
		return jdbc.queryForObject(
				"select id from academic_session where date '2026-10-07' between starts_on and ends_on", Long.class);
	}

	/** Adds a child of class 3, with a primary guardian (a receipt needs one), straight into the database. */
	long addStudent(String admissionNo, String joinedOn) {
		long id = jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
				+ "father_occupation, joined_on, created_by) values (?, 'Aryan', date '2018-05-14', 'M', '3', 'Jakhal', "
				+ "'OTHER', ?::date, ?) returning id", Long.class, admissionNo, joinedOn, anyUserId());
		long guardianId = jdbc.queryForObject("insert into guardian (name, phone) values ('Ramesh', ?) returning id",
				Long.class, "+9198" + String.format("%08d", ++guardianSeq));
		jdbc.update("insert into student_guardian (student_id, guardian_id, relation, is_primary) "
				+ "values (?, ?, 'FATHER', true)", id, guardianId);
		return id;
	}

	/** Puts a child on a bus straight into the database, bypassing the admission/transport endpoints. */
	void addBusEnrolment(long studentId, long[] route, String busFeePerMonth, String fromDate) {
		jdbc.update("insert into transport_enrolment (student_id, uses_bus, route_id, stop_id, from_date, "
				+ "bus_fee_per_month, reason, created_by) values (?, true, ?, ?, ?::date, ?, 'ADMISSION', ?)",
				studentId, route[0], route[1], fromDate, new java.math.BigDecimal(busFeePerMonth), anyUserId());
	}

	/** Adds a van, a route and one stop straight into the database. Returns {routeId, stopId}. */
	long[] addBusRoute(String name) {
		long vehicle = addVehicle("Van " + name);
		long route = jdbc.queryForObject("insert into route (name, vehicle_id) values (?, ?) returning id", Long.class,
				name, vehicle);
		long stop = jdbc.queryForObject("insert into route_stop (route_id, name, seq_no, morning_time, evening_time) "
				+ "values (?, 'Jakhal', 1, time '07:40', time '15:00') returning id", Long.class, route);
		return new long[] { route, stop };
	}

	ResultActions get(String token, String url) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", bearer(token)));
	}

	ResultActions post(String token, String url, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post(url).header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	ResultActions put(String token, String url, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.put(url).header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

}

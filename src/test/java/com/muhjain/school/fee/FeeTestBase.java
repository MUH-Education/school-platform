package com.muhjain.school.fee;

import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;


/**
 * Story for the Phase 7 tests. Today is 7 Oct 2026, 10:00 school time. Neelam (office admin) types the fees,
 * Priya (admissions desk) can also, the owner (Rishabh) is the only one who can correct a payment.
 */
abstract class FeeTestBase extends AbstractIntegrationTest {

	String office;

	String desk;

	String owner;

	String transport;

	@BeforeEach
	void addPeople() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		office = tokenFor(addUser("+919812340002", Role.OFFICE_ADMIN));
		desk = tokenFor(addUser("+919812340004", Role.ADMISSIONS_DESK));
		owner = tokenFor(addUser("+919812340001", Role.OWNER));
		transport = tokenFor(addUser("+919812340003", Role.TRANSPORT_INCHARGE));
	}

	long currentSessionId() {
		return jdbc.queryForObject("select id from academic_session where is_current", Long.class);
	}

	/** Adds a child of class 3 straight into the database. */
	long addStudent(String admissionNo, String joinedOn) {
		return jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
				+ "father_occupation, joined_on) values (?, 'Aryan', date '2018-05-14', 'M', '3', 'Jakhal', 'OTHER', "
				+ "?::date) returning id", Long.class, admissionNo, joinedOn);
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

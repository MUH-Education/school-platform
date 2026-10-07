package com.muhjain.school.trip;

import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Story for the Phase 4 tests. Today is 7 Oct 2026, 7:48 in the morning (school time).
 * Route 4 on Van 4: Jakhal 07:40, Kanheri 07:55. Balwan is its attendant. Route 7 on Van 7 has Hari.
 * Aryan and Siya ride Route 4 (Jakhal), Meera rides Route 4 (Kanheri), Dev rides Route 7.
 */
abstract class TripTestBase extends AbstractIntegrationTest {

	static final String TODAY = "2026-10-07";

	long route4;

	long route7;

	long jakhal;

	long kanheri;

	long route7Stop;

	long aryan;

	long siya;

	long meera;

	long dev;

	AppUser balwan;

	AppUser hari;

	AppUser office;

	String balwanToken;

	String hariToken;

	String officeToken;

	@BeforeEach
	void addTripStory() {
		clock.setInstant(Instant.parse("2026-10-07T02:18:00Z")); // 7:48 school time
		long van4 = addVehicle("Van 4");
		long van7 = addVehicle("Van 7");
		route4 = addRoute("Route 4", van4);
		route7 = addRoute("Route 7", van7);
		jakhal = addStop(route4, "Jakhal", 1, "07:40", "14:30");
		kanheri = addStop(route4, "Kanheri", 2, "07:55", "14:20");
		route7Stop = addStop(route7, "Fatehabad Road", 1, "07:30", "14:40");
		balwan = addUser("+919812340011", Role.ATTENDANT);
		hari = addUser("+919812340012", Role.ATTENDANT);
		office = addUser("+919812340002", Role.OFFICE_ADMIN);
		addAssignment(van4, balwan.getStaffId(), "ATTENDANT", "2026-04-01", null, false);
		addAssignment(van7, hari.getStaffId(), "ATTENDANT", "2026-04-01", null, false);
		aryan = addChild("Aryan", "M", route4, jakhal, "2026-04-01");
		siya = addChild("Siya", "F", route4, jakhal, "2026-04-01");
		meera = addChild("Meera", "F", route4, kanheri, "2026-04-01");
		dev = addChild("Dev", "M", route7, route7Stop, "2026-04-01");
		balwanToken = tokenFor(balwan);
		hariToken = tokenFor(hari);
		officeToken = tokenFor(office);
	}

	long addRoute(String name, long vehicleId) {
		return jdbc.queryForObject("insert into route (name, vehicle_id) values (?, ?) returning id", Long.class,
				name, vehicleId);
	}

	long addStop(long routeId, String name, int seq, String morning, String evening) {
		return jdbc.queryForObject("insert into route_stop (route_id, name, seq_no, morning_time, evening_time) "
				+ "values (?, ?, ?, ?::time, ?::time) returning id", Long.class, routeId, name, seq, morning,
				evening);
	}

	long addChild(String name, String gender, long routeId, long stopId, String from) {
		long id = jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
				+ "father_occupation, joined_on) values (?, ?, '2018-05-14', ?, '3', 'Jakhal', 'OTHER', '2026-04-01') "
				+ "returning id", Long.class, "T-" + (System.nanoTime() % 1_000_000_000_000L), name, gender);
		jdbc.update("insert into transport_enrolment (student_id, route_id, stop_id, from_date) values (?, ?, ?, "
				+ "?::date)", id, routeId, stopId, from);
		return id;
	}

	static String mark(long studentId, String event, String outcome, String date, String at) {
		return "{\"studentId\":" + studentId + ",\"eventType\":\"" + event + "\",\"outcome\":\"" + outcome
				+ "\",\"serviceDate\":\"" + date + "\",\"occurredAt\":\"" + at + "\"}";
	}

	/** A morning tap for today at the given time, for example {@code "07:42:10"}. */
	static String morning(long studentId, String outcome, String time) {
		return mark(studentId, "BOARDED_MORNING", outcome, TODAY, TODAY + "T" + time + "+05:30");
	}

	ResultActions sendMarks(String token, String... marks) throws Exception {
		return mockMvc.perform(post("/api/v1/trips/marks").header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"marks\":[" + String.join(",", marks) + "]}"));
	}

	ResultActions getAs(String token, String url) throws Exception {
		return mockMvc.perform(get(url).header("Authorization", bearer(token)));
	}

	int rows() {
		return jdbc.queryForObject("select count(*) from boarding_event", Integer.class);
	}

	String outcomeOf(long studentId, String event) {
		var list = jdbc.queryForList("select outcome from boarding_event where student_id = ? and event_type = ?",
				String.class, studentId, event);
		return list.isEmpty() ? null : list.get(0);
	}

}

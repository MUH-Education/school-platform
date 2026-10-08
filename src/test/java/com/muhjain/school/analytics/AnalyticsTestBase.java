package com.muhjain.school.analytics;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The fixed school of the Phase 8 tests: 12 children with known fees. Today is 7 Oct 2026, 10:00 school time.
 * Every plan is MONTHLY: school ₹12,000 (₹1,000 a month), bus ₹6,000 (₹500 a month, from 1 Apr). By 7 Oct seven dues
 * have come (Apr to Oct), so school is ₹7,000 due so far and bus ₹3,500.
 * <pre>
 * name    class village  occupation        bus      school paid  bus paid  → status
 * Aarav   1     Jakhal   FARMER_SMALL      Route A  7000         3500      ON_TIME
 * Bhavya  1     Jakhal   FARMER_SMALL      -        5000         -         DELAYED   (Sep due is 36 days late)
 * Charu   2     Jakhal   GOVT_EMPLOYEE     Route A  7000         0         DEFAULTED (bus: Apr due is 189 days late)
 * Dev     2     Kalwa    SHOPKEEPER        -        2000         -         DEFAULTED
 * Esha    3     Kalwa    SHOPKEEPER        Route B  7000         3500      ON_TIME
 * Farhan  3     Dhand    LABOUR            -        no plan      -         (none)
 * Gauri   5     Dhand    FARMER_LARGE      Route B  5000         3500      DELAYED
 * Harsh   5     Tohana   TEACHER_PROF.     -        7000         -         ON_TIME
 * Ishan   LKG   Tohana   PRIVATE_JOB       -        7000         -         ON_TIME
 * Jiya    LKG   Tohana   PRIVATE_JOB       -        2000         -         DEFAULTED
 * Kabir   8     Jakhal   OTHER             -        7000         -         ON_TIME
 * Lata    10    Kalwa    EX_SERVICEMAN     Route A  5000         3500      DELAYED
 * </pre>
 * So: 5 on time, 3 delayed, 3 defaulted, 1 without a plan. 5 children use the bus (3 on Route A, 2 on Route B).
 */
abstract class AnalyticsTestBase extends AbstractIntegrationTest {

	String owner;

	String office;

	String desk;

	String transport;

	String attendant;

	long routeA;

	long routeB;

	/** Child name → id. */
	final Map<String, Long> ids = new HashMap<>();

	@BeforeEach
	void twelveChildren() throws Exception {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		owner = tokenFor(addUser("+919812340001", Role.OWNER));
		office = tokenFor(addUser("+919812340002", Role.OFFICE_ADMIN));
		transport = tokenFor(addUser("+919812340003", Role.TRANSPORT_INCHARGE));
		desk = tokenFor(addUser("+919812340004", Role.ADMISSIONS_DESK));
		attendant = tokenFor(addUser("+919812340005", Role.ATTENDANT));
		long[] a = addBusRoute("Route A");
		long[] b = addBusRoute("Route B");
		routeA = a[0];
		routeB = b[0];
		ids.clear();
		child("Aarav", "1", "Jakhal", "FARMER_SMALL", a, 7000, 3500);
		child("Bhavya", "1", "Jakhal", "FARMER_SMALL", null, 5000, 0);
		child("Charu", "2", "Jakhal", "GOVT_EMPLOYEE", a, 7000, 0);
		child("Dev", "2", "Kalwa", "SHOPKEEPER", null, 2000, 0);
		child("Esha", "3", "Kalwa", "SHOPKEEPER", b, 7000, 3500);
		child("Farhan", "3", "Dhand", "LABOUR", null, -1, 0);
		child("Gauri", "5", "Dhand", "FARMER_LARGE", b, 5000, 3500);
		child("Harsh", "5", "Tohana", "TEACHER_PROFESSIONAL", null, 7000, 0);
		child("Ishan", "LKG", "Tohana", "PRIVATE_JOB", null, 7000, 0);
		child("Jiya", "LKG", "Tohana", "PRIVATE_JOB", null, 2000, 0);
		child("Kabir", "8", "Jakhal", "OTHER", null, 7000, 0);
		child("Lata", "10", "Kalwa", "EX_SERVICEMAN", a, 5000, 3500);
	}

	/** @param schoolPaid -1 means no fee plan at all */
	void child(String name, String className, String village, String occupation, long[] route, int schoolPaid,
			int busPaid) throws Exception {
		long id = jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
				+ "father_occupation, joined_on) values (?, ?, date '2018-05-14', 'M', ?, ?, ?, date '2026-04-01') "
				+ "returning id", Long.class, "T-" + name, name, className, village, occupation);
		ids.put(name, id);
		if (route != null) {
			jdbc.update("insert into transport_enrolment (student_id, route_id, stop_id, from_date) "
					+ "values (?, ?, ?, date '2026-04-01')", id, route[0], route[1]);
		}
		if (schoolPaid < 0) {
			return;
		}
		put(office, "/api/v1/students/" + id + "/fee-plan", "{\"schoolFee\":12000,\"busFee\":"
				+ ((route != null) ? 6000 : 0) + ",\"payFrequency\":\"MONTHLY\"}").andExpect(status().isOk());
		String lines = "";
		if (schoolPaid > 0) {
			lines += "{\"feeHead\":\"SCHOOL\",\"amount\":" + schoolPaid + "}";
		}
		if (busPaid > 0) {
			lines += (lines.isEmpty() ? "" : ",") + "{\"feeHead\":\"BUS\",\"amount\":" + busPaid + "}";
		}
		if (!lines.isEmpty()) {
			post(office, "/api/v1/students/" + id + "/payments", "{\"mode\":\"CASH\",\"lines\":[" + lines + "]}")
				.andExpect(status().isCreated());
		}
	}

	/** Adds a child with no plan and no bus, straight into the database. */
	long plainChild(String name, String className, String village) {
		return jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
				+ "father_occupation, joined_on) values (?, ?, date '2018-05-14', 'M', ?, ?, 'OTHER', date '2026-04-01') "
				+ "returning id", Long.class, "X-" + name, name, className, village);
	}

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

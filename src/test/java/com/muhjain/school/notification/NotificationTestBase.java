package com.muhjain.school.notification;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A small school on 7 Oct 2026 (Wednesday, 10 am school time), shared by the notification tests:
 * <ul>
 * <li>Punia family (+919811100001): Aryan (3, Route 4) and Siya (11). Dues: Aryan Sep + Oct, Siya Oct.</li>
 * <li>Chauhan family (+919811100002): Kavya (10), Jakhal. On the stop list. No dues.</li>
 * <li>Malik family (+919811100003): Riya (10, Route 4) — Riya has LEFT the school.</li>
 * <li>Sharma family (+919811100004): Dev (10). Dues: Aug, Sep, Oct.</li>
 * <li>Mohan (5): no phone saved at all.</li>
 * <li>Staff: Balwan (attendant of Route 4's van, and Aryan's father: +919811100001), Jagdish (driver of the van),
 * Sunita (teacher), Karan (teacher, turned off).</li>
 * <li>Enquiries: Suresh (open, +919811100005), Meena (lost, +919811100002 = the Chauhan phone).</li>
 * </ul>
 */
abstract class NotificationTestBase extends AbstractIntegrationTest {


	long punia;

	long chauhan;

	long sharma;

	long route4;

	long aryan;

	long siya;

	long balwan;

	long jagdish;

	long sunita;

	long dev;

	long sessionId;

	@BeforeEach
	void smallSchool() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		sessionId = jdbc.queryForObject("select id from academic_session where name = '2026-27'", Long.class);
		long van = addVehicle("Van 4");
		route4 = jdbc.queryForObject("insert into route (name, vehicle_id) values ('Route 4', ?) returning id", Long.class,
				van);
		long stop = jdbc.queryForObject("insert into route_stop (route_id, name, seq_no, morning_time, evening_time) "
				+ "values (?, 'Jakhal', 1, time '07:40', time '15:00') returning id", Long.class, route4);

		punia = guardian("Rajesh Punia", "+919811100001");
		chauhan = guardian("Vikram Chauhan", "+919811100002");
		long malik = guardian("Suresh Malik", "+919811100003");
		sharma = guardian("Anil Sharma", "+919811100004");

		aryan = child("A-2026-101", "Aryan Punia", "3", "Jakhal", punia);
		siya = child("A-2026-102", "Siya Punia", "11", "Jakhal", punia);
		child("A-2026-103", "Kavya Chauhan", "10", "Jakhal", chauhan);
		long riya = child("A-2026-104", "Riya Malik", "10", "Tohana", malik);
		dev = child("A-2026-105", "Dev Sharma", "10", "Tohana", sharma);
		child("A-2026-106", "Mohan", "5", "Tohana", null);
		jdbc.update("update student set status = 'LEFT', left_on = date '2026-09-30', left_reason = 'OTHER' where id = ?",
				riya);
		bus(aryan, stop);
		bus(riya, stop);

		due(aryan, "2026-09-01", "2500");
		due(aryan, "2026-10-01", "2500");
		due(siya, "2026-10-01", "3500");
		due(dev, "2026-08-01", "3200");
		due(dev, "2026-09-01", "3200");
		due(dev, "2026-10-01", "3200");

		balwan = staff("Balwan", "ATTENDANT", "+919811100001");
		jagdish = staff("Jagdish", "DRIVER", "+919811100010");
		sunita = staff("Sunita", "TEACHER", "+919811100011");
		long karan = staff("Karan", "TEACHER", "+919811100012");
		jdbc.update("update staff set active = false where id = ?", karan);
		addAssignment(van, jagdish, "DRIVER", "2026-04-01", null, false);
		addAssignment(van, balwan, "ATTENDANT", "2026-04-01", null, false);

		enquiry("Suresh", "+919811100005", "1", "VISITED");
		enquiry("Meena", "+919811100002", "2", "LOST");

		jdbc.update("insert into message_stop (guardian_id, reason) values (?, 'WROTE_STOP')", chauhan);
	}

	long guardian(String name, String phone) {
		return jdbc.queryForObject("insert into guardian (name, phone) values (?, ?) returning id", Long.class, name,
				phone);
	}

	long child(String admissionNo, String name, String className, String village, Long guardianId) {
		long id = jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
				+ "father_occupation, joined_on, created_by) values (?, ?, date '2016-05-14', 'M', ?, ?, 'OTHER', "
				+ "date '2026-04-01', ?) returning id", Long.class, admissionNo, name, className, village, anyUserId());
		if (guardianId != null) {
			jdbc.update("insert into student_guardian (student_id, guardian_id, relation, is_primary) "
					+ "values (?, ?, 'FATHER', true)", id, guardianId);
		}
		return id;
	}

	void bus(long studentId, long stopId) {
		jdbc.update("insert into transport_enrolment (student_id, uses_bus, route_id, stop_id, from_date, "
				+ "bus_fee_per_month, reason, created_by) values (?, true, ?, ?, date '2026-04-01', 800, 'ADMISSION', ?)",
				studentId, route4, stopId, anyUserId());
	}

	void due(long studentId, String month, String amount) {
		jdbc.update("insert into fee_due (student_id, session_id, month, school_amount) values (?, ?, ?::date, ?)",
				studentId, sessionId, month, new BigDecimal(amount));
	}

	long staff(String name, String type, String phone) {
		long id = addStaff(name, type);
		jdbc.update("update staff set phone = ? where id = ?", phone, id);
		return id;
	}

	void enquiry(String parent, String phone, String cls, String status) {
		jdbc.update("insert into enquiry (session_id, parent_name, phone, village, class_sought, source, status, "
				+ "status_before_lost, lost_reason, created_by) values (?, ?, ?, 'Tohana', ?, 'WALK_IN', ?, ?, ?, ?)",
				sessionId, parent, phone, cls, status, "LOST".equals(status) ? "CONTACTED" : null,
				"LOST".equals(status) ? "OTHER" : null, anyUserId());
	}

}

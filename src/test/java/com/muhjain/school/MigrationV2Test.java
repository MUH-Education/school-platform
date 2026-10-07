package com.muhjain.school;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The database rules of V2__vehicles_staff_routes.sql. They hold even if the Java code has a bug. */
class MigrationV2Test extends AbstractIntegrationTest {

	private long vehicleId;

	private long driverId;

	@BeforeEach
	void addVehicleAndDriver() {
		vehicleId = addVehicle("Van 4", "HR 23 A 1104");
		driverId = addStaff("Jagdish", "DRIVER");
	}

	private long addVehicle(String name, String registrationNo) {
		return jdbc.queryForObject("insert into vehicle (name, registration_no, vehicle_type, seats, monthly_cost, "
				+ "owned_by) values (?, ?, 'SMALL_VAN', 14, 30300.00, 'CONTRACTOR') returning id", Long.class, name,
				registrationNo);
	}

	@Test
	void vehicleNameAndRegistrationAreUniqueIgnoringCaseAndSpaces() {
		assertThatThrownBy(() -> addVehicle("van4", "OTHER 1"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> addVehicle("Van 5", "hr23a1104"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(addVehicle("Van 5", "HR 23 A 2000")).isPositive();
	}

	@Test
	void vehicleNeedsSeatsAboveZeroAndKnownType() {
		assertThatThrownBy(() -> jdbc.update("insert into vehicle (name, registration_no, vehicle_type, seats, "
				+ "monthly_cost, owned_by) values ('V', 'R1', 'SMALL_VAN', 0, 1, 'SCHOOL')"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("insert into vehicle (name, registration_no, vehicle_type, seats, "
				+ "monthly_cost, owned_by) values ('V', 'R1', 'TRUCK', 14, 1, 'SCHOOL')"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void oneVehicleHasOneRowPerPaperType() {
		jdbc.update("insert into vehicle_document (vehicle_id, doc_type, valid_till) values (?, 'INSURANCE', "
				+ "'2026-10-28')", vehicleId);
		assertThatThrownBy(() -> jdbc.update("insert into vehicle_document (vehicle_id, doc_type, valid_till) "
				+ "values (?, 'INSURANCE', '2027-10-28')", vehicleId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void driverNeedsALicenceAndOthersHaveNone() {
		assertThatThrownBy(() -> jdbc.update("insert into staff (name, phone, staff_type) "
				+ "values ('No licence', '+919811100001', 'DRIVER')"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("insert into staff (name, phone, staff_type, licence_no, "
				+ "licence_valid_till) values ('Balwan', '+919811100002', 'ATTENDANT', 'X1', '2030-01-01')"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("insert into staff (name, phone, staff_type) "
				+ "values ('Bad phone', '9811100003', 'HELPER')"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void attendantUserMustPointAtARealStaffRow() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into app_user (phone, role, staff_id) values ('+919812340009', 'ATTENDANT', 999999)"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void assignmentDatesAreChecked() {
		// to_date before from_date
		assertThatThrownBy(() -> assign("2026-10-16", "2026-10-12", false))
			.isInstanceOf(DataIntegrityViolationException.class);
		// a temporary row must have a to_date
		assertThatThrownBy(() -> assign("2026-10-12", null, true))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(assign("2026-10-12", "2026-10-16", true)).isEqualTo(1);
		assertThat(assign("2026-04-01", null, false)).isEqualTo(1);
	}

	private int assign(String from, String to, boolean temporary) {
		return jdbc.update("insert into vehicle_assignment (vehicle_id, staff_id, duty, from_date, to_date, "
				+ "temporary) values (?, ?, 'DRIVER', ?::date, ?::date, ?)", vehicleId, driverId, from, to, temporary);
	}

	@Test
	void oneVehicleRunsAtMostOneActiveRoute() {
		jdbc.update("insert into route (name, vehicle_id) values ('Route 4', ?)", vehicleId);
		assertThatThrownBy(() -> jdbc.update("insert into route (name, vehicle_id) values ('Route 40', ?)", vehicleId))
			.isInstanceOf(DataIntegrityViolationException.class);
		// A turned-off route does not count, and a route with no vehicle is allowed many times.
		jdbc.update("insert into route (name, vehicle_id, active) values ('Old route', ?, false)", vehicleId);
		jdbc.update("insert into route (name) values ('Route A')");
		jdbc.update("insert into route (name) values ('Route B')");
	}

	@Test
	void routeNameIsUniqueIgnoringCaseAndSpaces() {
		jdbc.update("insert into route (name) values ('Route 4')");
		assertThatThrownBy(() -> jdbc.update("insert into route (name) values ('route4')"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void stopNumbersAreUniqueInARouteButMayBeSwappedInOneTransaction() {
		long routeId = jdbc.queryForObject("insert into route (name) values ('Route 4') returning id", Long.class);
		jdbc.update("insert into route_stop (route_id, name, seq_no) values (?, 'Sadhanwas', 1), (?, 'Jakhal', 2)",
				routeId, routeId);
		assertThatThrownBy(() -> jdbc.update("insert into route_stop (route_id, name, seq_no) values (?, 'X', 2)",
				routeId))
			.isInstanceOf(DataIntegrityViolationException.class);
		// Swapping 1 and 2 in one statement is fine, because the check waits until the end.
		jdbc.update("update route_stop set seq_no = 3 - seq_no where route_id = ?", routeId);
		assertThat(jdbc.queryForObject("select name from route_stop where route_id = ? and seq_no = 1", String.class,
				routeId))
			.isEqualTo("Jakhal");
	}

}

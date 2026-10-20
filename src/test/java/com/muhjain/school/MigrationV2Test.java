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

	// db-design's created_by on vehicle_assignment is not null, unlike before.
	private long userId;

	@BeforeEach
	void addVehicleAndDriver() {
		vehicleId = addVehicle("Van 4", "HR 23 A 1104");
		driverId = addStaff("Jagdish", "DRIVER");
		userId = addUser("+919812340001", com.muhjain.school.model.enums.Role.OWNER).getId();
	}

	private long addVehicle(String name, String registrationNo) {
		return jdbc.queryForObject("insert into vehicle (name, registration_no, vehicle_type, seats, monthly_cost, "
				+ "owned_by) values (?, ?, 'SMALL_VAN', 14, 30300.00, 'CONTRACTOR') returning id", Long.class, name,
				registrationNo);
	}

	// db-design (docs/08-decisions.md) makes this a plain, case-sensitive unique column: an exact repeat
	// of the name or registration is refused, but "van4"/"hr23a1104" are different strings from
	// "Van 4"/"HR 23 A 1104" and are both allowed. The old case/space-insensitive check is gone.
	@Test
	void vehicleNameAndRegistrationAreUniqueExactlyButNotIgnoringCaseAndSpaces() {
		assertThatThrownBy(() -> addVehicle("Van 4", "OTHER 1")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> addVehicle("Van 5", "HR 23 A 1104"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(addVehicle("van4", "hr23a1104")).isPositive();
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

	// db-design's check is one-directional: a DRIVER must have a licence, but it no longer forbids a
	// non-driver from also having one (the old two-way rule is gone, see docs/08-decisions.md).
	@Test
	void driverNeedsALicenceButOthersMayAlsoHaveOne() {
		assertThatThrownBy(() -> jdbc.update("insert into staff (name, phone, staff_type) "
				+ "values ('No licence', '+919811100001', 'DRIVER')"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.update("insert into staff (name, phone, staff_type, licence_no, "
				+ "licence_valid_till) values ('Balwan', '+919811100002', 'ATTENDANT', 'X1', '2030-01-01')"))
			.isEqualTo(1);
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
				+ "temporary, created_by) values (?, ?, 'DRIVER', ?::date, ?::date, ?, ?)", vehicleId, driverId, from,
				to, temporary, userId);
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

	// Same regression as the vehicle name: plain unique, not case/space-insensitive any more.
	@Test
	void routeNameIsUniqueExactlyButNotIgnoringCaseAndSpaces() {
		jdbc.update("insert into route (name) values ('Route 4')");
		assertThatThrownBy(() -> jdbc.update("insert into route (name) values ('Route 4')"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.update("insert into route (name) values ('route4')")).isEqualTo(1);
	}

	// db-design's seq_no uniqueness is a plain partial unique index (active stops only), not the old
	// deferrable constraint — so a same-statement swap no longer works. The service must instead bump
	// every seq_no of the route out of the way first, as the V2 comment says.
	@Test
	void stopNumbersAreUniqueAmongActiveStopsAndReorderNeedsTwoSteps() {
		long routeId = jdbc.queryForObject("insert into route (name) values ('Route 4') returning id", Long.class);
		jdbc.update("insert into route_stop (route_id, name, seq_no) values (?, 'Sadhanwas', 1), (?, 'Jakhal', 2)",
				routeId, routeId);
		assertThatThrownBy(() -> jdbc.update("insert into route_stop (route_id, name, seq_no) values (?, 'X', 2)",
				routeId))
			.isInstanceOf(DataIntegrityViolationException.class);
		// A direct swap in one statement collides, because the index is not deferrable any more.
		assertThatThrownBy(() -> jdbc.update("update route_stop set seq_no = 3 - seq_no where route_id = ?", routeId))
			.isInstanceOf(DataIntegrityViolationException.class);
		// The two-step move (seq_no += 1000, then the real order) works.
		jdbc.update("update route_stop set seq_no = seq_no + 1000 where route_id = ?", routeId);
		jdbc.update("update route_stop set seq_no = 1 where route_id = ? and name = 'Jakhal'", routeId);
		jdbc.update("update route_stop set seq_no = 2 where route_id = ? and name = 'Sadhanwas'", routeId);
		assertThat(jdbc.queryForObject("select name from route_stop where route_id = ? and seq_no = 1", String.class,
				routeId))
			.isEqualTo("Jakhal");
		// An inactive (removed) stop does not count toward the uniqueness.
		jdbc.update("update route_stop set active = false where route_id = ? and name = 'Jakhal'", routeId);
		assertThat(jdbc.update("insert into route_stop (route_id, name, seq_no) values (?, 'New stop', 1)", routeId))
			.isEqualTo(1);
	}

}

package com.muhjain.school.staff;

import java.time.LocalDate;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** The queries behind "who is on the vehicle on day D". Example data: Jagdish drives Van 4 from 1 Apr 2026. */
class VehicleAssignmentRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private VehicleAssignmentRepository assignments;

	private long van4;

	private long van1;

	private long jagdish;

	private long surender;

	@BeforeEach
	void addPeople() {
		van4 = addVehicle("Van 4");
		van1 = addVehicle("Van 1");
		jagdish = addStaff("Jagdish", "DRIVER");
		surender = addStaff("Surender", "DRIVER");
		addAssignment(van4, jagdish, "DRIVER", "2026-04-01", null, false);
		addAssignment(van4, surender, "DRIVER", "2026-10-12", "2026-10-16", true);
	}

	private static LocalDate day(String iso) {
		return LocalDate.parse(iso);
	}

	@Test
	void coveringDayGivesBothRowsInsideTheTemporaryDays() {
		assertThat(assignments.coveringDay(van4, day("2026-10-14")))
			.extracting(VehicleAssignment::getStaffId)
			.containsExactly(jagdish, surender);
	}

	@Test
	void coveringDayGivesOnlyThePermanentRowOutsideTheTemporaryDays() {
		assertThat(assignments.coveringDay(van4, day("2026-10-11")))
			.extracting(VehicleAssignment::getStaffId)
			.containsExactly(jagdish);
		assertThat(assignments.coveringDay(van4, day("2026-10-17")))
			.extracting(VehicleAssignment::getStaffId)
			.containsExactly(jagdish);
	}

	@Test
	void firstAndLastDayAreInside() {
		assertThat(assignments.coveringDay(van4, day("2026-10-12"))).hasSize(2);
		assertThat(assignments.coveringDay(van4, day("2026-10-16"))).hasSize(2);
	}

	@Test
	void beforeTheFirstDayThereIsNoRow() {
		assertThat(assignments.coveringDay(van4, day("2026-03-31"))).isEmpty();
	}

	@Test
	void otherVehiclesAreNotMixedIn() {
		assertThat(assignments.coveringDay(van1, day("2026-10-14"))).isEmpty();
		assertThat(assignments.coveringDay(day("2026-10-14"))).hasSize(2);
		assertThat(assignments.coveringDay(java.util.List.of(van1), day("2026-10-14"))).isEmpty();
	}

	@Test
	void rowsOfOnePersonBetweenTwoDays() {
		assertThat(assignments.ofStaffBetween(jagdish, day("2027-01-01"), day("2027-01-05"))).hasSize(1);
		assertThat(assignments.ofStaffBetween(surender, day("2026-10-16"), day("2026-10-20"))).hasSize(1);
		assertThat(assignments.ofStaffBetween(surender, day("2026-10-17"), day("2026-10-20"))).isEmpty();
		assertThat(assignments.ofStaffBetween(surender, day("2026-10-01"), day("2026-10-11"))).isEmpty();
	}

	@Test
	void personIsOnAVehicleTodayOrLater() {
		assertThat(assignments.existsOnOrAfter(jagdish, day("2030-01-01"))).isTrue();
		assertThat(assignments.existsOnOrAfter(surender, day("2026-10-16"))).isTrue();
		assertThat(assignments.existsOnOrAfter(surender, day("2026-10-17"))).isFalse();
	}

}

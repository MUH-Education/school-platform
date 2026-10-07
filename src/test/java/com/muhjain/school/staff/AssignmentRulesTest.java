package com.muhjain.school.staff;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule 9: "Who is on the vehicle on day D? The temporary row covering D wins. Else the permanent row covering D."
 * No Spring, no database. Example data from the docs: Jagdish drives Van 4 from 1 Apr 2026. He is on leave
 * 12 to 16 Oct, so Surender drives Van 4 on those days (temporary).
 */
class AssignmentRulesTest {

	private static final Long VAN_4 = 4L;

	private static final Long JAGDISH = 20L;

	private static final Long SURENDER = 21L;

	private static final Long BALWAN = 30L;

	private static final Long RAJPAL = 31L;

	private static LocalDate day(String iso) {
		return LocalDate.parse(iso);
	}

	private static VehicleAssignment permanent(Long staffId, Duty duty, String from, String to) {
		return new VehicleAssignment(VAN_4, staffId, duty, day(from), (to == null) ? null : day(to), false, null,
				null);
	}

	private static VehicleAssignment temporary(Long staffId, Duty duty, String from, String to) {
		return new VehicleAssignment(VAN_4, staffId, duty, day(from), day(to), true, ChangeReason.ON_LEAVE, null);
	}

	private static Optional<Long> driverOn(List<VehicleAssignment> rows, String iso) {
		return AssignmentRules.pick(rows, Duty.DRIVER, day(iso)).map(VehicleAssignment::getStaffId);
	}

	private final List<VehicleAssignment> jagdishWithLeave = List.of(
			permanent(JAGDISH, Duty.DRIVER, "2026-04-01", null),
			temporary(SURENDER, Duty.DRIVER, "2026-10-12", "2026-10-16"));

	@Test
	void temporaryDriverWinsOnlyInsideHisDates() {
		assertThat(driverOn(jagdishWithLeave, "2026-10-12")).contains(SURENDER);
		assertThat(driverOn(jagdishWithLeave, "2026-10-14")).contains(SURENDER);
		assertThat(driverOn(jagdishWithLeave, "2026-10-16")).contains(SURENDER);
		assertThat(driverOn(jagdishWithLeave, "2026-10-11")).contains(JAGDISH);
		assertThat(driverOn(jagdishWithLeave, "2026-10-17")).contains(JAGDISH);
	}

	@Test
	void afterTemporaryPeriodThePermanentDriverIsBack() {
		// Nobody changed Jagdish's row. On 17 Oct he drives again, and also on 1 Jan.
		assertThat(driverOn(jagdishWithLeave, "2026-10-17")).contains(JAGDISH);
		assertThat(driverOn(jagdishWithLeave, "2027-01-01")).contains(JAGDISH);
	}

	@Test
	void permanentRowOnlyCoversItsOwnDays() {
		List<VehicleAssignment> rows = List.of(permanent(JAGDISH, Duty.DRIVER, "2026-04-01", "2026-10-31"),
				permanent(SURENDER, Duty.DRIVER, "2026-11-01", null));

		assertThat(driverOn(rows, "2026-03-31")).isEmpty();
		assertThat(driverOn(rows, "2026-10-31")).contains(JAGDISH);
		assertThat(driverOn(rows, "2026-11-01")).contains(SURENDER);
	}

	@Test
	void noRowMeansNoPerson() {
		assertThat(driverOn(List.of(), "2026-10-07")).isEmpty();
	}

	@Test
	void eachDutyIsDecidedOnItsOwn() {
		List<VehicleAssignment> rows = List.of(permanent(JAGDISH, Duty.DRIVER, "2026-04-01", null),
				permanent(BALWAN, Duty.ATTENDANT, "2026-04-01", null),
				temporary(SURENDER, Duty.DRIVER, "2026-10-12", "2026-10-16"));

		Map<Duty, VehicleAssignment> onDay = AssignmentRules.crew(rows, day("2026-10-14"));

		// The temporary driver does not touch the attendant. Nobody is the helper.
		assertThat(onDay.get(Duty.DRIVER).getStaffId()).isEqualTo(SURENDER);
		assertThat(onDay.get(Duty.ATTENDANT).getStaffId()).isEqualTo(BALWAN);
		assertThat(onDay).doesNotContainKey(Duty.HELPER);
	}

	@Test
	void temporaryAttendantAndHelperWorkTheSameWay() {
		List<VehicleAssignment> rows = List.of(permanent(BALWAN, Duty.ATTENDANT, "2026-04-01", null),
				permanent(RAJPAL, Duty.HELPER, "2026-04-01", null),
				temporary(SURENDER, Duty.HELPER, "2026-10-12", "2026-10-16"));

		assertThat(AssignmentRules.crew(rows, day("2026-10-13")).get(Duty.ATTENDANT).getStaffId()).isEqualTo(BALWAN);
		assertThat(AssignmentRules.crew(rows, day("2026-10-13")).get(Duty.HELPER).getStaffId()).isEqualTo(SURENDER);
		assertThat(AssignmentRules.crew(rows, day("2026-10-20")).get(Duty.HELPER).getStaffId()).isEqualTo(RAJPAL);
	}

	@Test
	void crewHasNoRowForADutyThatIsNotCovered() {
		List<VehicleAssignment> rows = List.of(permanent(JAGDISH, Duty.DRIVER, "2026-11-01", null));

		assertThat(AssignmentRules.crew(rows, day("2026-10-07"))).isEmpty();
	}

}

package com.muhjain.school.staff;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rules 6 to 8 and 10 to 12 of docs/phases/phase-2-vehicles-staff-routes.md, through the real URL.
 * Story: Jagdish drives Van 4 from 1 Apr 2026. Today is 7 Oct 2026.
 */
class AssignmentChangeTest extends AbstractIntegrationTest {

	@Autowired
	private AssignmentService assignmentService;

	private AppUser transport;

	private String token;

	private long van4;

	private long van1;

	private long jagdish;

	private long surender;

	private long balwan;

	@BeforeEach
	void addStory() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		transport = addUser("+919812340003", Role.TRANSPORT_INCHARGE);
		token = tokenFor(transport);
		van4 = addVehicle("Van 4");
		van1 = addVehicle("Van 1");
		jagdish = addStaff("Jagdish", "DRIVER");
		surender = addStaff("Surender", "DRIVER");
		balwan = addStaff("Balwan", "ATTENDANT");
		addAssignment(van4, jagdish, "DRIVER", "2026-04-01", null, false);
	}

	private ResultActions change(long vehicleId, String json) throws Exception {
		return mockMvc.perform(post("/api/v1/vehicles/" + vehicleId + "/assignments")
			.header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private static String permanent(String duty, long staffId, String from) {
		return "{\"duty\":\"" + duty + "\",\"staffId\":" + staffId + ",\"fromDate\":\"" + from + "\"}";
	}

	private static String temporary(String duty, long staffId, String from, String to) {
		return "{\"duty\":\"" + duty + "\",\"staffId\":" + staffId + ",\"fromDate\":\"" + from + "\",\"toDate\":\"" + to
				+ "\",\"temporary\":true,\"reason\":\"ON_LEAVE\"}";
	}

	private Long driverOn(long vehicleId, String day) {
		CrewMember driver = assignmentService.onDate(vehicleId, LocalDate.parse(day)).driver();
		return (driver != null) ? driver.staffId() : null;
	}

	private int rowCount() {
		return jdbc.queryForObject("select count(*) from vehicle_assignment", Integer.class);
	}

	@Test
	void permanentChangeClosesTheOldRow() throws Exception {
		change(van4, "{\"duty\":\"DRIVER\",\"staffId\":" + surender + ",\"fromDate\":\"2026-11-01\","
				+ "\"reason\":\"LEFT_SCHOOL\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.vehicleId").value(van4))
			.andExpect(jsonPath("$.staffId").value(surender))
			.andExpect(jsonPath("$.staffName").value("Surender"))
			.andExpect(jsonPath("$.duty").value("DRIVER"))
			.andExpect(jsonPath("$.fromDate").value("2026-11-01"))
			.andExpect(jsonPath("$.toDate").isEmpty())
			.andExpect(jsonPath("$.temporary").value(false))
			.andExpect(jsonPath("$.reason").value("LEFT_SCHOOL"))
			.andExpect(jsonPath("$.createdBy").value(transport.getId()));

		// Jagdish's row ends the day before. The new row has no end.
		assertThat(jdbc.queryForObject("select to_date::text from vehicle_assignment where staff_id = ?",
				String.class, jagdish))
			.isEqualTo("2026-10-31");
		assertThat(jdbc.queryForObject("select to_date from vehicle_assignment where staff_id = ?", Object.class,
				surender))
			.isNull();
		assertThat(driverOn(van4, "2026-10-31")).isEqualTo(jagdish);
		assertThat(driverOn(van4, "2026-11-01")).isEqualTo(surender);
		assertThat(driverOn(van4, "2027-06-01")).isEqualTo(surender);
	}

	@Test
	void temporaryChangeDoesNotTouchThePermanentRow() throws Exception {
		change(van4, temporary("DRIVER", surender, "2026-10-12", "2026-10-16")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.temporary").value(true))
			.andExpect(jsonPath("$.fromDate").value("2026-10-12"))
			.andExpect(jsonPath("$.toDate").value("2026-10-16"))
			.andExpect(jsonPath("$.reason").value("ON_LEAVE"));

		assertThat(jdbc.queryForObject("select to_date from vehicle_assignment where staff_id = ?", Object.class,
				jagdish))
			.isNull();
		assertThat(rowCount()).isEqualTo(2);
		assertThat(driverOn(van4, "2026-10-11")).isEqualTo(jagdish);
		assertThat(driverOn(van4, "2026-10-14")).isEqualTo(surender);
		assertThat(driverOn(van4, "2026-10-17")).isEqualTo(jagdish);
	}

	@Test
	void firstPersonOnAVehicleHasNobodyToClose() throws Exception {
		change(van4, permanent("ATTENDANT", balwan, "2026-10-07")).andExpect(status().isCreated());

		assertThat(rowCount()).isEqualTo(2);
		assertThat(assignmentService.onDate(van4, LocalDate.of(2026, 10, 8)).attendant().staffId())
			.isEqualTo(balwan);
	}

	@Test
	void changeAfterAnEarlierClosedRowWorks() throws Exception {
		// Jagdish left on 30 Sep. Nobody drives Van 1. Surender starts on 1 Oct.
		addAssignment(van1, jagdish, "DRIVER", "2026-01-01", "2026-09-30", false);

		change(van1, permanent("DRIVER", surender, "2026-10-01")).andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("select to_date::text from vehicle_assignment where vehicle_id = ? "
				+ "and staff_id = ?", String.class, van1, jagdish))
			.isEqualTo("2026-09-30");
		assertThat(driverOn(van1, "2026-10-07")).isEqualTo(surender);
	}

	@Test
	void samePersonOnTwoVehiclesSameDayGives409() throws Exception {
		long rajpal = addStaff("Rajpal", "DRIVER");
		addAssignment(van1, rajpal, "DRIVER", "2026-04-01", null, false);

		change(van4, temporary("DRIVER", rajpal, "2026-10-12", "2026-10-16")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("STAFF_BUSY"))
			.andExpect(jsonPath("$.message").value("Rajpal drives Van 1 on these days."));

		assertThat(rowCount()).isEqualTo(2);
		assertThat(driverOn(van4, "2026-10-14")).isEqualTo(jagdish);
	}

	@Test
	void permanentChangeOfABusyPersonGives409AndKeepsTheOldRow() throws Exception {
		long rajpal = addStaff("Rajpal", "DRIVER");
		addAssignment(van1, rajpal, "DRIVER", "2026-04-01", null, false);

		change(van4, permanent("DRIVER", rajpal, "2026-11-01")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("STAFF_BUSY"));

		assertThat(jdbc.queryForObject("select to_date from vehicle_assignment where staff_id = ?", Object.class,
				jagdish))
			.isNull();
	}

	@Test
	void busyMessageNamesTheDutyOfAnAttendant() throws Exception {
		addAssignment(van1, balwan, "ATTENDANT", "2026-04-01", null, false);

		change(van4, permanent("ATTENDANT", balwan, "2026-11-01")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Balwan is the attendant of Van 1 on these days."));
	}

	@Test
	void personWhoLeavesAnotherVehicleBeforeTheNewDateIsFree() throws Exception {
		long rajpal = addStaff("Rajpal", "DRIVER");
		addAssignment(van1, rajpal, "DRIVER", "2026-04-01", "2026-10-11", false);

		change(van4, temporary("DRIVER", rajpal, "2026-10-12", "2026-10-16")).andExpect(status().isCreated());

		// But one day earlier it clashes.
		long hari = addStaff("Hari", "DRIVER");
		addAssignment(van1, hari, "DRIVER", "2026-12-01", null, false);
		change(van4, temporary("DRIVER", hari, "2026-11-28", "2026-12-01")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("STAFF_BUSY"));
	}

	@Test
	void personAlreadyOnTheSameVehicleIsBusyToo() throws Exception {
		change(van4, permanent("DRIVER", jagdish, "2026-11-01")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("STAFF_BUSY"))
			.andExpect(jsonPath("$.message").value("Jagdish drives Van 4 on these days."));
	}

	@Test
	void driverDutyNeedsADriver() throws Exception {
		change(van4, permanent("DRIVER", balwan, "2026-11-01")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("WRONG_STAFF_TYPE"))
			.andExpect(jsonPath("$.message").value("Balwan works as ATTENDANT. The DRIVER duty needs a DRIVER."));
		change(van4, permanent("ATTENDANT", surender, "2026-11-01")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("WRONG_STAFF_TYPE"));
		long helper = addStaff("Rajpal", "HELPER");
		change(van4, permanent("HELPER", helper, "2026-11-01")).andExpect(status().isCreated());
		assertThat(driverOn(van4, "2026-11-02")).isEqualTo(jagdish);
	}

	@Test
	void driverWithEndedLicenceCannotBeAssigned() throws Exception {
		long old = addStaff("Old licence", "DRIVER", LocalDate.of(2026, 10, 11));

		change(van4, temporary("DRIVER", old, "2026-10-12", "2026-10-16")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("LICENCE_ENDED"))
			.andExpect(jsonPath("$.message").value("Old licence's licence ended on 11 Oct 2026."));
		assertThat(rowCount()).isEqualTo(1);

		// Licence valid till 12 Oct. It "ends before the from date" only when from is 13 Oct or later.
		long lastDay = addStaff("Last day", "DRIVER", LocalDate.of(2026, 10, 12));
		change(van4, temporary("DRIVER", lastDay, "2026-10-12", "2026-10-12")).andExpect(status().isCreated());
		long late = addStaff("Late", "DRIVER", LocalDate.of(2026, 10, 12));
		change(van1, permanent("DRIVER", late, "2026-10-13")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("LICENCE_ENDED"));
	}

	@Test
	void licenceIsNotCheckedForAttendants() throws Exception {
		change(van4, permanent("ATTENDANT", balwan, "2026-10-07")).andExpect(status().isCreated());
	}

	@Test
	void temporaryChangeNeedsAnEndDayAndPermanentChangeHasNone() throws Exception {
		change(van4, "{\"duty\":\"DRIVER\",\"staffId\":" + surender + ",\"fromDate\":\"2026-10-12\","
				+ "\"temporary\":true}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.toDate").value("is required for a temporary change"));
		change(van4, "{\"duty\":\"DRIVER\",\"staffId\":" + surender + ",\"fromDate\":\"2026-11-01\","
				+ "\"toDate\":\"2026-11-05\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.toDate").value("is only for a temporary change"));
		change(van4, temporary("DRIVER", surender, "2026-10-16", "2026-10-12")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.toDate").value("must not be before fromDate"));
		change(van4, "{\"staffId\":" + surender + "}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.duty").exists())
			.andExpect(jsonPath("$.fields.fromDate").exists());
		assertThat(rowCount()).isEqualTo(1);
	}

	@Test
	void permanentChangeMustStartAfterTheCurrentPersonStarted() throws Exception {
		change(van4, permanent("DRIVER", surender, "2026-04-01")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("FROM_DATE_TOO_EARLY"));
		change(van4, permanent("DRIVER", surender, "2026-03-01")).andExpect(status().isConflict());
		change(van4, permanent("DRIVER", surender, "2026-04-02")).andExpect(status().isCreated());
		// Jagdish's row now ends on 1 Apr. The change must also be later than Surender's start.
		long hari = addStaff("Hari", "DRIVER");
		change(van4, permanent("DRIVER", hari, "2026-04-02")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("FROM_DATE_TOO_EARLY"));
	}

	@Test
	void twoTemporaryDriversCannotCoverTheSameDay() throws Exception {
		change(van4, temporary("DRIVER", surender, "2026-10-12", "2026-10-16")).andExpect(status().isCreated());
		long hari = addStaff("Hari", "DRIVER");

		change(van4, temporary("DRIVER", hari, "2026-10-16", "2026-10-18")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("TEMPORARY_OVERLAP"))
			.andExpect(jsonPath("$.message")
				.value("Surender already covers the DRIVER duty of Van 4 from 12 to 16 Oct."));
		// The day after is free.
		change(van4, temporary("DRIVER", hari, "2026-10-17", "2026-10-18")).andExpect(status().isCreated());
		assertThat(driverOn(van4, "2026-10-17")).isEqualTo(hari);
	}

	@Test
	void personWhoIsTurnedOffCannotBeAssigned() throws Exception {
		jdbc.update("update staff set active = false where id = ?", surender);

		change(van4, permanent("DRIVER", surender, "2026-11-01")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("STAFF_INACTIVE"));
	}

	@Test
	void vehicleThatIsTurnedOffCannotGetPeople() throws Exception {
		jdbc.update("update vehicle set active = false where id = ?", van1);

		change(van1, permanent("DRIVER", surender, "2026-11-01")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("VEHICLE_INACTIVE"));
	}

	@Test
	void unknownVehicleGives404AndUnknownPersonGives400() throws Exception {
		change(999, permanent("DRIVER", surender, "2026-11-01")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("NOT_FOUND"));
		change(van4, permanent("DRIVER", 999, "2026-11-01")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.staffId").value("does not exist"));
	}

	@Test
	void officeAdminCannotChangePeopleAndNothingIsSaved() throws Exception {
		String adminToken = tokenFor(addUser("+919812340004", Role.OFFICE_ADMIN));

		mockMvc.perform(post("/api/v1/vehicles/" + van4 + "/assignments")
			.header("Authorization", bearer(adminToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content(permanent("DRIVER", surender, "2026-11-01")))
			.andExpect(status().isForbidden());

		assertThat(rowCount()).isEqualTo(1);
	}

	@Test
	void theLoggedInUserIsCreatedByNotAnIdFromTheBody() throws Exception {
		// A "createdBy" in the body is not part of the request. It is ignored.
		change(van4, "{\"duty\":\"ATTENDANT\",\"staffId\":" + balwan + ",\"fromDate\":\"2026-10-07\",\"createdBy\":999}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.createdBy").value(transport.getId()));
	}

	@Test
	void manyChangesInARowKeepTheHistoryInOrder() throws Exception {
		long hari = addStaff("Hari", "DRIVER");
		change(van4, permanent("DRIVER", surender, "2026-11-01")).andExpect(status().isCreated());
		change(van4, permanent("DRIVER", hari, "2026-12-01")).andExpect(status().isCreated());

		List<Map<String, Object>> rows = jdbc.queryForList("select staff_id, from_date::text f, to_date::text t "
				+ "from vehicle_assignment where vehicle_id = ? order by from_date", van4);
		assertThat(rows).extracting(r -> r.get("t")).containsExactly("2026-10-31", "2026-11-30", null);
		assertThat(driverOn(van4, "2026-12-15")).isEqualTo(hari);
	}

}

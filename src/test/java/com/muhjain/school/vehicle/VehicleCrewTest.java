package com.muhjain.school.vehicle;

import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 2.8: the vehicle screens show the people of the day.
 * Story: Jagdish drives Van 4 from 1 Apr 2026. Surender replaces him 12 to 16 Oct (temporary). Balwan is the attendant.
 */
class VehicleCrewTest extends AbstractIntegrationTest {

	private String token;

	private long van4;

	private long van1;

	private long jagdish;

	private long surender;

	private long balwan;

	@BeforeEach
	void addStory() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		token = tokenFor(addUser("+919812340003", Role.TRANSPORT_INCHARGE));
		van4 = addVehicle("Van 4");
		van1 = addVehicle("Van 1");
		jagdish = addStaff("Jagdish", "DRIVER");
		surender = addStaff("Surender", "DRIVER");
		balwan = addStaff("Balwan", "ATTENDANT");
		addAssignment(van4, jagdish, "DRIVER", "2026-04-01", null, false);
		addAssignment(van4, balwan, "ATTENDANT", "2026-04-01", null, false);
		addAssignment(van4, surender, "DRIVER", "2026-10-12", "2026-10-16", true);
	}

	private org.springframework.test.web.servlet.ResultActions getJson(String url) throws Exception {
		return mockMvc.perform(get(url).header("Authorization", bearer(token)));
	}

	@Test
	void vehicleShowsTodaysDriverAndAttendant() throws Exception {
		getJson("/api/v1/vehicles/" + van4).andExpect(status().isOk())
			.andExpect(jsonPath("$.driver.staffId").value(jagdish))
			.andExpect(jsonPath("$.driver.name").value("Jagdish"))
			.andExpect(jsonPath("$.driver.phone").value("+919811100000"))
			.andExpect(jsonPath("$.driver.temporary").value(false))
			.andExpect(jsonPath("$.attendant.staffId").value(balwan))
			.andExpect(jsonPath("$.helper").isEmpty());
	}

	@Test
	void insideTheLeaveTheNewDriverShowsAndOutsideTheOldOne() throws Exception {
		// "Today" is moved with the clock. 14 Oct is inside the leave.
		clock.setInstant(Instant.parse("2026-10-14T04:30:00Z"));
		getJson("/api/v1/vehicles/" + van4).andExpect(jsonPath("$.driver.name").value("Surender"))
			.andExpect(jsonPath("$.driver.temporary").value(true))
			.andExpect(jsonPath("$.attendant.name").value("Balwan"));

		clock.setInstant(Instant.parse("2026-10-17T04:30:00Z"));
		getJson("/api/v1/vehicles/" + van4).andExpect(jsonPath("$.driver.name").value("Jagdish"))
			.andExpect(jsonPath("$.driver.temporary").value(false));
	}

	@Test
	void dateParameterShowsAnotherDayWithoutMovingTheClock() throws Exception {
		getJson("/api/v1/vehicles/" + van4 + "?date=2026-10-14").andExpect(jsonPath("$.driver.name").value("Surender"));
		getJson("/api/v1/vehicles/" + van4 + "?date=2026-10-11").andExpect(jsonPath("$.driver.name").value("Jagdish"));
		getJson("/api/v1/vehicles/" + van4 + "?date=2026-10-17").andExpect(jsonPath("$.driver.name").value("Jagdish"));
		getJson("/api/v1/vehicles/" + van4 + "?date=2026-03-31").andExpect(jsonPath("$.driver").isEmpty());
		getJson("/api/v1/vehicles?date=2026-10-14").andExpect(jsonPath("$[0].driver.name").value("Surender"));
		getJson("/api/v1/vehicles/" + van4 + "?date=14-10-2026").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$.fields.date").value("has the wrong format"));
	}

	@Test
	void listShowsTheCrewOfEveryVehicle() throws Exception {
		getJson("/api/v1/vehicles").andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			// Van 4 was added first, so it is first.
			.andExpect(jsonPath("$[0].name").value("Van 4"))
			.andExpect(jsonPath("$[0].driver.name").value("Jagdish"))
			.andExpect(jsonPath("$[0].attendant.name").value("Balwan"))
			.andExpect(jsonPath("$[1].name").value("Van 1"))
			.andExpect(jsonPath("$[1].driver").isEmpty())
			.andExpect(jsonPath("$[1].attendant").isEmpty());
	}

	@Test
	void aRowShowsOnlyOnItsOwnDays() throws Exception {
		// Surender drives Van 1 from 1 Oct to 11 Oct. Today is 7 Oct, so the Van 1 screen shows him. Not on 12 Oct.
		addAssignment(van1, surender, "DRIVER", "2026-10-01", "2026-10-11", false);
		getJson("/api/v1/vehicles/" + van1).andExpect(jsonPath("$.driver.name").value("Surender"));
		getJson("/api/v1/vehicles/" + van1 + "?date=2026-10-12").andExpect(jsonPath("$.driver").isEmpty());
	}

	@Test
	void staffListShowsWhereEachPersonWorksOnTheDay() throws Exception {
		// 7 Oct: Jagdish and Balwan are on Van 4. Surender is on no vehicle.
		getJson("/api/v1/staff").andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("Jagdish"))
			.andExpect(jsonPath("$[0].worksOn.vehicleName").value("Van 4"))
			.andExpect(jsonPath("$[0].worksOn.duty").value("DRIVER"))
			.andExpect(jsonPath("$[0].worksOn.temporary").value(false))
			.andExpect(jsonPath("$[1].name").value("Surender"))
			.andExpect(jsonPath("$[1].worksOn").isEmpty())
			.andExpect(jsonPath("$[2].worksOn.duty").value("ATTENDANT"));

		// 14 Oct: Surender drives Van 4. Jagdish is on leave, so he works on no vehicle that day.
		clock.setInstant(Instant.parse("2026-10-14T04:30:00Z"));
		getJson("/api/v1/staff").andExpect(jsonPath("$[0].worksOn").isEmpty())
			.andExpect(jsonPath("$[1].worksOn.vehicleName").value("Van 4"))
			.andExpect(jsonPath("$[1].worksOn.temporary").value(true));
	}

}

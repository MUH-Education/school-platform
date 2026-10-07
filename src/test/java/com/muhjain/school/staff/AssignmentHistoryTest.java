package com.muhjain.school.staff;

import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /vehicles/{id}/assignments}: who worked on the vehicle, newest first. */
class AssignmentHistoryTest extends AbstractIntegrationTest {

	private String token;

	private long van4;

	@BeforeEach
	void addStory() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		token = tokenFor(addUser("+919812340003", Role.TRANSPORT_INCHARGE));
		van4 = addVehicle("Van 4");
		long jagdish = addStaff("Jagdish", "DRIVER");
		long surender = addStaff("Surender", "DRIVER");
		long balwan = addStaff("Balwan", "ATTENDANT");
		addAssignment(van4, jagdish, "DRIVER", "2026-04-01", null, false);
		addAssignment(van4, balwan, "ATTENDANT", "2026-04-01", null, false);
		addAssignment(van4, surender, "DRIVER", "2026-10-12", "2026-10-16", true);
	}

	@Test
	void historyIsNewestFirst() throws Exception {
		mockMvc.perform(get("/api/v1/vehicles/" + van4 + "/assignments").header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			// The temporary row started last.
			.andExpect(jsonPath("$[0].staffName").value("Surender"))
			.andExpect(jsonPath("$[0].temporary").value(true))
			.andExpect(jsonPath("$[0].fromDate").value("2026-10-12"))
			.andExpect(jsonPath("$[0].toDate").value("2026-10-16"))
			// The two rows of 1 Apr: the one saved later comes first.
			.andExpect(jsonPath("$[1].staffName").value("Balwan"))
			.andExpect(jsonPath("$[1].duty").value("ATTENDANT"))
			.andExpect(jsonPath("$[2].staffName").value("Jagdish"))
			.andExpect(jsonPath("$[2].toDate").isEmpty());
	}

	@Test
	void vehicleWithNobodyHasAnEmptyHistoryAndUnknownVehicleGives404() throws Exception {
		long van1 = addVehicle("Van 1");

		mockMvc.perform(get("/api/v1/vehicles/" + van1 + "/assignments").header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/v1/vehicles/999/assignments").header("Authorization", bearer(token)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void officeAdminMayReadItButTheAdmissionsDeskMayNot() throws Exception {
		String adminToken = tokenFor(addUser("+919812340004", Role.OFFICE_ADMIN));
		String deskToken = tokenFor(addUser("+919812340005", Role.ADMISSIONS_DESK));

		mockMvc.perform(get("/api/v1/vehicles/" + van4 + "/assignments").header("Authorization", bearer(adminToken)))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/vehicles/" + van4 + "/assignments").header("Authorization", bearer(deskToken)))
			.andExpect(status().isForbidden());
	}

}

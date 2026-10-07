package com.muhjain.school.staff;

import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The "Staff" tests of docs/phases/phase-2-vehicles-staff-routes.md (rule 4). */
class StaffApiTest extends AbstractIntegrationTest {

	private static final String JAGDISH = "{\"name\":\"Jagdish\",\"phone\":\"98123 40010\",\"staffType\":\"DRIVER\","
			+ "\"licenceNo\":\"HR2620110012345\",\"licenceValidTill\":\"2029-03-31\"}";

	private String token;

	@BeforeEach
	void login() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		token = tokenFor(addUser("+919812340003", Role.TRANSPORT_INCHARGE));
	}

	private ResultActions send(MockHttpServletRequestBuilder builder, String body) throws Exception {
		return mockMvc.perform(
				builder.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void driverIsSavedWithLicenceAndNormalizedPhone() throws Exception {
		send(post("/api/v1/staff"), JAGDISH).andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.name").value("Jagdish"))
			.andExpect(jsonPath("$.phone").value("+919812340010"))
			.andExpect(jsonPath("$.staffType").value("DRIVER"))
			.andExpect(jsonPath("$.licenceNo").value("HR2620110012345"))
			.andExpect(jsonPath("$.licenceValidTill").value("2029-03-31"))
			.andExpect(jsonPath("$.licenceStatus").value("VALID"))
			.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void driverNeedsLicenceNumberAndDate() throws Exception {
		send(post("/api/v1/staff"), "{\"name\":\"Jagdish\",\"phone\":\"9812340010\",\"staffType\":\"DRIVER\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$.fields.licenceNo").value("is required for a driver"));
		send(post("/api/v1/staff"), "{\"name\":\"Jagdish\",\"phone\":\"9812340010\",\"staffType\":\"DRIVER\","
				+ "\"licenceNo\":\"X1\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.licenceValidTill").value("is required for a driver"));
		assertThat(jdbc.queryForObject("select count(*) from staff", Integer.class)).isZero();
	}

	@Test
	void licenceIsIgnoredForAttendantAndHelper() throws Exception {
		send(post("/api/v1/staff"), "{\"name\":\"Balwan\",\"phone\":\"9812340011\",\"staffType\":\"ATTENDANT\","
				+ "\"licenceNo\":\"X1\",\"licenceValidTill\":\"2029-03-31\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.licenceNo").isEmpty())
			.andExpect(jsonPath("$.licenceValidTill").isEmpty())
			.andExpect(jsonPath("$.licenceStatus").isEmpty());
	}

	@Test
	void badPhoneGives400() throws Exception {
		send(post("/api/v1/staff"), "{\"name\":\"Balwan\",\"phone\":\"12345\",\"staffType\":\"HELPER\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"));
	}

	@Test
	void updateChangesThePersonAndLicenceStatusFollowsTheDate() throws Exception {
		long id = addStaff("Jagdish", "DRIVER");

		// Licence ends 28 Oct 2026, 21 days after 7 Oct.
		send(put("/api/v1/staff/" + id), "{\"name\":\"Jagdish Kumar\",\"phone\":\"09812340012\","
				+ "\"staffType\":\"DRIVER\",\"licenceNo\":\"HR99\",\"licenceValidTill\":\"2026-10-28\",\"active\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Jagdish Kumar"))
			.andExpect(jsonPath("$.phone").value("+919812340012"))
			.andExpect(jsonPath("$.licenceStatus").value("ENDING_SOON"))
			.andExpect(jsonPath("$.licenceDaysLeft").value(21));

		// A driver who becomes a helper loses the licence fields.
		send(put("/api/v1/staff/" + id), "{\"name\":\"Jagdish Kumar\",\"phone\":\"09812340012\","
				+ "\"staffType\":\"HELPER\",\"licenceNo\":\"HR99\",\"licenceValidTill\":\"2026-10-28\",\"active\":true}")
			.andExpect(jsonPath("$.licenceNo").isEmpty());
	}

	@Test
	void listShowsEveryoneAndUnknownIdGives404() throws Exception {
		addStaff("Jagdish", "DRIVER");
		addStaff("Balwan", "ATTENDANT");

		mockMvc.perform(get("/api/v1/staff").header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].name").value("Jagdish"));
		send(put("/api/v1/staff/999"), "{\"name\":\"X\",\"phone\":\"9812340012\",\"staffType\":\"HELPER\","
				+ "\"active\":true}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void deleteTurnsThePersonOffAndKeepsTheRow() throws Exception {
		long id = addStaff("Balwan", "ATTENDANT");

		mockMvc.perform(delete("/api/v1/staff/" + id).header("Authorization", bearer(token)))
			.andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("select active from staff where id = ?", Boolean.class, id)).isFalse();
	}

	@Test
	void personOnAVehicleCannotBeTurnedOff() throws Exception {
		// 7 Oct 2026. Jagdish drives Van 4 from 1 Apr 2026 with no end date.
		long van4 = addVehicle("Van 4");
		long jagdish = addStaff("Jagdish", "DRIVER");
		addAssignment(van4, jagdish, "DRIVER", "2026-04-01", null, false);

		mockMvc.perform(delete("/api/v1/staff/" + jagdish).header("Authorization", bearer(token)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("STAFF_ASSIGNED"))
			.andExpect(jsonPath("$.message").value("Jagdish works on Van 4 as DRIVER. Change who works there first."));
		// The same rule holds for a PUT with active=false.
		send(put("/api/v1/staff/" + jagdish), "{\"name\":\"Jagdish\",\"phone\":\"9812340010\","
				+ "\"staffType\":\"DRIVER\",\"licenceNo\":\"X1\",\"licenceValidTill\":\"2029-03-31\","
				+ "\"active\":false}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("STAFF_ASSIGNED"));
		assertThat(jdbc.queryForObject("select active from staff where id = ?", Boolean.class, jagdish)).isTrue();
	}

	@Test
	void personWhoseLastDayWasYesterdayCanBeTurnedOff() throws Exception {
		long van4 = addVehicle("Van 4");
		long jagdish = addStaff("Jagdish", "DRIVER");
		addAssignment(van4, jagdish, "DRIVER", "2026-04-01", "2026-10-06", false);

		mockMvc.perform(delete("/api/v1/staff/" + jagdish).header("Authorization", bearer(token)))
			.andExpect(status().isNoContent());

		// A row that starts in the future also blocks: "today or later".
		long surender = addStaff("Surender", "DRIVER");
		addAssignment(van4, surender, "DRIVER", "2026-11-01", null, false);
		mockMvc.perform(delete("/api/v1/staff/" + surender).header("Authorization", bearer(token)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("STAFF_ASSIGNED"));
	}

	@Test
	void typeCannotChangeAfterThePersonWorkedADuty() throws Exception {
		long van4 = addVehicle("Van 4");
		long jagdish = addStaff("Jagdish", "DRIVER");
		addAssignment(van4, jagdish, "DRIVER", "2026-04-01", "2026-10-06", false);

		send(put("/api/v1/staff/" + jagdish), "{\"name\":\"Jagdish\",\"phone\":\"9812340010\","
				+ "\"staffType\":\"HELPER\",\"active\":true}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("STAFF_TYPE_IN_USE"));
		// A person with no duty yet may change type.
		long balwan = addStaff("Balwan", "ATTENDANT");
		send(put("/api/v1/staff/" + balwan), "{\"name\":\"Balwan\",\"phone\":\"9812340011\","
				+ "\"staffType\":\"HELPER\",\"active\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.staffType").value("HELPER"));
	}

}

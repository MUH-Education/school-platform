package com.muhjain.school.vehicle;

import java.time.Instant;
import java.time.LocalDate;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /vehicles/attention}: papers and licences that ended or end within 30 days. Today is 7 Oct 2026. */
class VehicleAttentionTest extends AbstractIntegrationTest {

	private String token;

	private long van4;

	private long van1;

	@BeforeEach
	void login() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		token = tokenFor(addUser("+919812340003", Role.TRANSPORT_INCHARGE));
		van4 = addVehicle("Van 4");
		van1 = addVehicle("Van 1");
	}

	private void saveDocuments(long vehicleId, String json) throws Exception {
		mockMvc.perform(put("/api/v1/vehicles/" + vehicleId + "/documents").header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json))
			.andExpect(status().isOk());
	}

	private ResultActions attention() throws Exception {
		return mockMvc.perform(get("/api/v1/vehicles/attention").header("Authorization", bearer(token)));
	}

	@Test
	void paperEndingInTenDaysIsListed() throws Exception {
		saveDocuments(van4, "{\"insurance\":\"2026-10-17\"}");

		attention().andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].kind").value("PAPER"))
			.andExpect(jsonPath("$[0].vehicleId").value(van4))
			.andExpect(jsonPath("$[0].vehicleName").value("Van 4"))
			.andExpect(jsonPath("$[0].docType").value("INSURANCE"))
			.andExpect(jsonPath("$[0].validTill").value("2026-10-17"))
			.andExpect(jsonPath("$[0].status").value("ENDING_SOON"))
			.andExpect(jsonPath("$[0].daysLeft").value(10))
			.andExpect(jsonPath("$[0].staffId").isEmpty());
	}

	@Test
	void endedAndEndingSoonAreListedAndTheRestIsNot() throws Exception {
		saveDocuments(van4, "{\"fitness\":\"2026-11-06\",\"insurance\":\"2026-11-07\",\"permit\":\"2026-10-04\"}");

		// 6 Nov is 30 days away: listed. 7 Nov is 31 days away: not listed. PUC has no date: not listed.
		attention().andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].docType").value("PERMIT"))
			.andExpect(jsonPath("$[0].status").value("ENDED"))
			.andExpect(jsonPath("$[0].daysLeft").value(-3))
			.andExpect(jsonPath("$[1].docType").value("FITNESS"))
			.andExpect(jsonPath("$[1].status").value("ENDING_SOON"))
			.andExpect(jsonPath("$[1].daysLeft").value(30));
	}

	@Test
	void driverLicenceIsListedAndSortedWithThePapers() throws Exception {
		saveDocuments(van1, "{\"puc\":\"2026-10-20\"}");
		addStaff("Jagdish", "DRIVER", LocalDate.of(2026, 10, 12));
		addStaff("Surender", "DRIVER", LocalDate.of(2026, 12, 1));
		addStaff("Balwan", "ATTENDANT");

		attention().andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].kind").value("LICENCE"))
			.andExpect(jsonPath("$[0].staffName").value("Jagdish"))
			.andExpect(jsonPath("$[0].daysLeft").value(5))
			.andExpect(jsonPath("$[0].vehicleId").isEmpty())
			.andExpect(jsonPath("$[1].kind").value("PAPER"))
			.andExpect(jsonPath("$[1].vehicleName").value("Van 1"));
	}

	@Test
	void turnedOffVehiclesAndDriversAreLeftOut() throws Exception {
		saveDocuments(van4, "{\"insurance\":\"2026-10-17\"}");
		long jagdish = addStaff("Jagdish", "DRIVER", LocalDate.of(2026, 10, 12));
		jdbc.update("update vehicle set active = false where id = ?", van4);
		jdbc.update("update staff set active = false where id = ?", jagdish);

		attention().andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void nothingToReportGivesAnEmptyList() throws Exception {
		saveDocuments(van4, "{\"insurance\":\"2027-10-17\"}");

		attention().andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void officeAdminMayReadIt() throws Exception {
		String adminToken = tokenFor(addUser("+919812340004", Role.OFFICE_ADMIN));

		mockMvc.perform(get("/api/v1/vehicles/attention").header("Authorization", bearer(adminToken)))
			.andExpect(status().isOk());
	}

}

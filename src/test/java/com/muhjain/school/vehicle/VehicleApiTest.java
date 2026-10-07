package com.muhjain.school.vehicle;

import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The "Vehicles" tests of docs/phases/phase-2-vehicles-staff-routes.md (rules 1 to 3). */
class VehicleApiTest extends AbstractIntegrationTest {

	private static final String VAN4 = "{\"name\":\"Van 4\",\"registrationNo\":\"HR 23 A 1104\","
			+ "\"vehicleType\":\"SMALL_VAN\",\"seats\":14,\"monthlyCost\":30300,\"ownedBy\":\"CONTRACTOR\"}";

	private String transportToken;

	@BeforeEach
	void login() {
		// 7 Oct 2026, 10:00 in Kolkata.
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		transportToken = tokenFor(addUser("+919812340003", Role.TRANSPORT_INCHARGE));
	}

	private ResultActions create(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/vehicles").header("Authorization", bearer(transportToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
			String body) throws Exception {
		return mockMvc.perform(builder.header("Authorization", bearer(transportToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private long createVan4() throws Exception {
		String json = create(VAN4).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.id")).longValue();
	}

	@Test
	void createSavesTheVehicleAndShowsFourMissingPapers() throws Exception {
		create(VAN4).andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.name").value("Van 4"))
			.andExpect(jsonPath("$.registrationNo").value("HR 23 A 1104"))
			.andExpect(jsonPath("$.vehicleType").value("SMALL_VAN"))
			.andExpect(jsonPath("$.seats").value(14))
			.andExpect(jsonPath("$.monthlyCost").value(30300.00))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.papersStatus").value("MISSING"))
			.andExpect(jsonPath("$.documents.length()").value(4))
			.andExpect(jsonPath("$.documents[0].docType").value("FITNESS"))
			.andExpect(jsonPath("$.documents[0].status").value("MISSING"))
			.andExpect(jsonPath("$.documents[0].validTill").isEmpty());
	}

	@Test
	void registrationNumberIsUniqueIgnoringCaseAndSpaces() throws Exception {
		createVan4();

		create(VAN4.replace("Van 4", "Van 9").replace("HR 23 A 1104", "hr23a1104"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("REGISTRATION_ALREADY_USED"));
		create(VAN4.replace("Van 4", "VAN4").replace("HR 23 A 1104", "HR 23 A 2000"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("VEHICLE_NAME_ALREADY_USED"));
		assertThat(jdbc.queryForObject("select count(*) from vehicle", Integer.class)).isEqualTo(1);
	}

	@Test
	void updateChangesDetailsAndChecksUniqueness() throws Exception {
		long van4 = createVan4();
		create(VAN4.replace("Van 4", "Van 5").replace("HR 23 A 1104", "HR 23 A 2000")).andExpect(status().isCreated());

		// Own name and registration again are fine. Other fields change.
		send(put("/api/v1/vehicles/" + van4), "{\"name\":\"van 4\",\"registrationNo\":\"HR23A1104\","
				+ "\"vehicleType\":\"MID_BUS\",\"seats\":26,\"monthlyCost\":31000.50,\"ownedBy\":\"SCHOOL\","
				+ "\"active\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("van 4"))
			.andExpect(jsonPath("$.vehicleType").value("MID_BUS"))
			.andExpect(jsonPath("$.seats").value(26))
			.andExpect(jsonPath("$.monthlyCost").value(31000.50));

		// Taking the name of Van 5 is not.
		send(put("/api/v1/vehicles/" + van4), "{\"name\":\"VAN 5\",\"registrationNo\":\"HR23A1104\","
				+ "\"vehicleType\":\"MID_BUS\",\"seats\":26,\"monthlyCost\":31000.50,\"ownedBy\":\"SCHOOL\","
				+ "\"active\":true}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("VEHICLE_NAME_ALREADY_USED"));
	}

	@Test
	void badInputGives400WithFields() throws Exception {
		create("{\"name\":\"\",\"registrationNo\":\"HR 1\",\"vehicleType\":\"SMALL_VAN\",\"seats\":0,"
				+ "\"monthlyCost\":-5,\"ownedBy\":\"SCHOOL\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$.fields.name").exists())
			.andExpect(jsonPath("$.fields.seats").exists())
			.andExpect(jsonPath("$.fields.monthlyCost").exists());
		create(VAN4.replace("30300", "30300.123")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.monthlyCost").exists());
		create(VAN4.replace("SMALL_VAN", "TRUCK")).andExpect(status().isBadRequest());
	}

	@Test
	void unknownVehicleGives404() throws Exception {
		mockMvc.perform(get("/api/v1/vehicles/999").header("Authorization", bearer(transportToken)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void deleteTurnsTheVehicleOffAndKeepsTheRow() throws Exception {
		long van4 = createVan4();

		mockMvc.perform(delete("/api/v1/vehicles/" + van4).header("Authorization", bearer(transportToken)))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/vehicles/" + van4).header("Authorization", bearer(transportToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
		// Turning off again changes nothing. A PUT with active=true turns it on.
		mockMvc.perform(delete("/api/v1/vehicles/" + van4).header("Authorization", bearer(transportToken)))
			.andExpect(status().isNoContent());
		send(put("/api/v1/vehicles/" + van4), VAN4.replace("}", ",\"active\":true}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void paperEndingIn21DaysIsEndingSoon() throws Exception {
		long van4 = createVan4();

		send(put("/api/v1/vehicles/" + van4 + "/documents"),
				"{\"fitness\":\"2027-01-10\",\"insurance\":\"2026-10-28\",\"permit\":\"2026-10-06\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.documents[0].docType").value("FITNESS"))
			.andExpect(jsonPath("$.documents[0].status").value("VALID"))
			.andExpect(jsonPath("$.documents[1].docType").value("INSURANCE"))
			.andExpect(jsonPath("$.documents[1].status").value("ENDING_SOON"))
			.andExpect(jsonPath("$.documents[1].daysLeft").value(21))
			.andExpect(jsonPath("$.documents[2].status").value("ENDED"))
			.andExpect(jsonPath("$.documents[2].daysLeft").value(-1))
			.andExpect(jsonPath("$.documents[3].docType").value("PUC"))
			.andExpect(jsonPath("$.documents[3].status").value("MISSING"))
			.andExpect(jsonPath("$.papersStatus").value("ENDED"));

		mockMvc.perform(get("/api/v1/vehicles/" + van4).header("Authorization", bearer(transportToken)))
			.andExpect(jsonPath("$.documents[1].status").value("ENDING_SOON"));
		mockMvc.perform(get("/api/v1/vehicles").header("Authorization", bearer(transportToken)))
			.andExpect(jsonPath("$[0].papersStatus").value("ENDED"));
	}

	@Test
	void savingDocumentsAgainReplacesAndRemoves() throws Exception {
		long van4 = createVan4();
		send(put("/api/v1/vehicles/" + van4 + "/documents"), "{\"insurance\":\"2026-10-28\",\"puc\":\"2027-02-01\"}")
			.andExpect(status().isOk());

		// Insurance gets a new date. PUC is left out, so it is removed.
		send(put("/api/v1/vehicles/" + van4 + "/documents"), "{\"insurance\":\"2027-10-27\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.documents[1].validTill").value("2027-10-27"))
			.andExpect(jsonPath("$.documents[1].status").value("VALID"))
			.andExpect(jsonPath("$.documents[3].status").value("MISSING"));
		assertThat(jdbc.queryForObject("select count(*) from vehicle_document where vehicle_id = ?", Integer.class,
				van4))
			.isEqualTo(1);
	}

	@Test
	void todayIsTheSchoolDayInKolkataNotInUtc() throws Exception {
		long van4 = createVan4();
		send(put("/api/v1/vehicles/" + van4 + "/documents"), "{\"insurance\":\"2026-10-06\"}").andExpect(status().isOk());

		// 6 Oct 19:00 UTC is 7 Oct 00:30 in Kolkata. A paper valid till 6 Oct has ended.
		clock.setInstant(Instant.parse("2026-10-06T19:00:00Z"));
		mockMvc.perform(get("/api/v1/vehicles/" + van4).header("Authorization", bearer(transportToken)))
			.andExpect(jsonPath("$.documents[1].status").value("ENDED"));

		// 6 Oct 17:00 UTC is 6 Oct 22:30 in Kolkata. Still the last valid day.
		clock.setInstant(Instant.parse("2026-10-06T17:00:00Z"));
		mockMvc.perform(get("/api/v1/vehicles/" + van4).header("Authorization", bearer(transportToken)))
			.andExpect(jsonPath("$.documents[1].status").value("ENDING_SOON"));
	}

	@Test
	void admissionsDeskGets403OnVehicles() throws Exception {
		long van4 = createVan4();
		String deskToken = tokenFor(addUser("+919812340005", Role.ADMISSIONS_DESK));

		mockMvc.perform(get("/api/v1/vehicles").header("Authorization", bearer(deskToken)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("FORBIDDEN"));
		mockMvc.perform(get("/api/v1/vehicles/" + van4).header("Authorization", bearer(deskToken)))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/staff").header("Authorization", bearer(deskToken)))
			.andExpect(status().isForbidden());
	}

	@Test
	void officeAdminCanGetButNotPostVehicles() throws Exception {
		long van4 = createVan4();
		String adminToken = tokenFor(addUser("+919812340004", Role.OFFICE_ADMIN));

		mockMvc.perform(get("/api/v1/vehicles").header("Authorization", bearer(adminToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(get("/api/v1/vehicles/" + van4).header("Authorization", bearer(adminToken)))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/vehicles").header("Authorization", bearer(adminToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content(VAN4.replace("Van 4", "Van 9").replace("HR 23 A 1104", "HR 23 A 9999")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("FORBIDDEN"));
		mockMvc.perform(delete("/api/v1/vehicles/" + van4).header("Authorization", bearer(adminToken)))
			.andExpect(status().isForbidden());
		// Nothing was changed by the refused calls.
		assertThat(jdbc.queryForObject("select count(*) from vehicle", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select active from vehicle where id = ?", Boolean.class, van4)).isTrue();
	}

	@Test
	void attendantGets403OnVehiclesAndStaff() throws Exception {
		String attendantToken = tokenFor(addUser("+919812340006", Role.ATTENDANT));

		mockMvc.perform(get("/api/v1/vehicles").header("Authorization", bearer(attendantToken)))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/staff").header("Authorization", bearer(attendantToken)))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/routes").header("Authorization", bearer(attendantToken)))
			.andExpect(status().isForbidden());
	}

}

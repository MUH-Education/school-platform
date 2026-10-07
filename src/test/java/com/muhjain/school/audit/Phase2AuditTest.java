package com.muhjain.school.audit;

import java.time.Instant;
import java.util.List;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 2.15: every create, update and assignment change writes one {@code audit_log} row with a readable line.
 * Today is 7 Oct 2026. The office user is a TRANSPORT_INCHARGE.
 */
class Phase2AuditTest extends AbstractIntegrationTest {

	private AppUser office;

	private String token;

	@BeforeEach
	void login() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		office = addUser("+919812340003", Role.TRANSPORT_INCHARGE);
		token = tokenFor(office);
	}

	private ResultActions send(MockHttpServletRequestBuilder builder, String body) throws Exception {
		return mockMvc.perform(
				builder.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private List<String> summaries(String entityType, long entityId) {
		return jdbc.queryForList("select summary from audit_log where entity_type = ? and entity_id = ? order by id",
				String.class, entityType, entityId);
	}

	private long idOf(ResultActions result) throws Exception {
		return ((Number) com.jayway.jsonpath.JsonPath.read(result.andReturn().getResponse().getContentAsString(),
				"$.id")).longValue();
	}

	private int auditCount() {
		return jdbc.queryForObject("select count(*) from audit_log", Integer.class);
	}

	private static final String VAN = "{\"name\":\"Van 4\",\"registrationNo\":\"HR 23 A 1104\","
			+ "\"vehicleType\":\"SMALL_VAN\",\"seats\":14,\"monthlyCost\":30300,\"ownedBy\":\"CONTRACTOR\"";

	@Test
	void vehicleCreateUpdateDocumentsAndTurnOff() throws Exception {
		long id = idOf(send(post("/api/v1/vehicles"), VAN + "}").andExpect(status().isCreated()));

		assertThat(summaries("VEHICLE", id)).containsExactly("Vehicle Van 4 (HR 23 A 1104) added");
		assertThat(jdbc.queryForObject("select action from audit_log where entity_id = ? and entity_type = 'VEHICLE'",
				String.class, id))
			.isEqualTo("CREATED");
		assertThat(jdbc.queryForObject("select changed_by from audit_log where entity_type = 'VEHICLE'", Long.class))
			.isEqualTo(office.getId());

		// Seats and cost change. The name is the same, so it is not in the line.
		send(put("/api/v1/vehicles/" + id), VAN.replace("\"seats\":14", "\"seats\":26")
				.replace("30300", "31000.50") + ",\"active\":true}")
			.andExpect(status().isOk());
		assertThat(summaries("VEHICLE", id)).last()
			.isEqualTo("Seats changed from 14 to 26. Monthly cost changed from 30300.00 to 31000.50.");
		assertThat(jdbc.queryForObject("select details->'seats'->>'new' from audit_log where entity_id = ? "
				+ "and action = 'UPDATED'", String.class, id))
			.isEqualTo("26");

		// The same save again changes nothing, so nothing is written.
		int before = auditCount();
		send(put("/api/v1/vehicles/" + id), VAN.replace("\"seats\":14", "\"seats\":26")
				.replace("30300", "31000.5") + ",\"active\":true}")
			.andExpect(status().isOk());
		assertThat(auditCount()).isEqualTo(before);

		send(put("/api/v1/vehicles/" + id + "/documents"), "{\"insurance\":\"2026-10-28\",\"puc\":\"2027-02-01\"}")
			.andExpect(status().isOk());
		assertThat(summaries("VEHICLE", id)).last()
			.isEqualTo("Insurance valid till set to 28 Oct 2026. PUC valid till set to 1 Feb 2027.");
		send(put("/api/v1/vehicles/" + id + "/documents"), "{\"insurance\":\"2027-10-27\"}").andExpect(status().isOk());
		assertThat(summaries("VEHICLE", id)).last()
			.isEqualTo("Insurance valid till changed from 28 Oct 2026 to 27 Oct 2027. PUC valid till removed.");

		before = auditCount();
		send(put("/api/v1/vehicles/" + id + "/documents"), "{\"insurance\":\"2027-10-27\"}").andExpect(status().isOk());
		assertThat(auditCount()).as("same dates again").isEqualTo(before);

		mockMvc.perform(delete("/api/v1/vehicles/" + id).header("Authorization", bearer(token)))
			.andExpect(status().isNoContent());
		assertThat(summaries("VEHICLE", id)).last().isEqualTo("Turned off.");
		before = auditCount();
		mockMvc.perform(delete("/api/v1/vehicles/" + id).header("Authorization", bearer(token)))
			.andExpect(status().isNoContent());
		assertThat(auditCount()).as("turning off an off vehicle").isEqualTo(before);
	}

	@Test
	void refusedChangesWriteNothing() throws Exception {
		send(post("/api/v1/vehicles"), VAN + "}").andExpect(status().isCreated());
		int before = auditCount();

		send(post("/api/v1/vehicles"), VAN + "}").andExpect(status().isConflict());
		send(post("/api/v1/staff"), "{\"name\":\"Jagdish\",\"phone\":\"9812340010\",\"staffType\":\"DRIVER\"}")
			.andExpect(status().isBadRequest());

		assertThat(auditCount()).isEqualTo(before);
	}

	@Test
	void staffCreateUpdateAndTurnOff() throws Exception {
		long id = idOf(send(post("/api/v1/staff"), "{\"name\":\"Jagdish\",\"phone\":\"98123 40010\","
				+ "\"staffType\":\"DRIVER\",\"licenceNo\":\"HR99\",\"licenceValidTill\":\"2029-03-31\"}")
			.andExpect(status().isCreated()));
		assertThat(summaries("STAFF", id)).containsExactly("Staff Jagdish added as DRIVER");

		send(put("/api/v1/staff/" + id), "{\"name\":\"Jagdish Kumar\",\"phone\":\"9812340012\","
				+ "\"staffType\":\"DRIVER\",\"licenceNo\":\"HR99\",\"licenceValidTill\":\"2030-03-31\",\"active\":true}")
			.andExpect(status().isOk());
		// The phone is written masked. The full number is never in the history.
		assertThat(summaries("STAFF", id)).last()
			.isEqualTo("Name changed from Jagdish to Jagdish Kumar. Phone changed from +91XXXXXX0010 to "
					+ "+91XXXXXX0012. Licence valid till changed from 31 Mar 2029 to 31 Mar 2030.");
		assertThat(jdbc.queryForObject("select details::text from audit_log where entity_type = 'STAFF' "
				+ "and action = 'UPDATED'", String.class))
			.doesNotContain("9812340010")
			.doesNotContain("9812340012");

		mockMvc.perform(delete("/api/v1/staff/" + id).header("Authorization", bearer(token)))
			.andExpect(status().isNoContent());
		assertThat(summaries("STAFF", id)).last().isEqualTo("Turned off.");
	}

	@Test
	void assignmentChangesAreWrittenOnTheVehicle() throws Exception {
		long van4 = addVehicle("Van 4");
		long jagdish = addStaff("Jagdish", "DRIVER");
		long surender = addStaff("Surender", "DRIVER");
		long balwan = addStaff("Balwan", "ATTENDANT");
		addAssignment(van4, jagdish, "DRIVER", "2026-04-01", null, false);

		// Temporary: the example from the phase file.
		send(post("/api/v1/vehicles/" + van4 + "/assignments"), "{\"duty\":\"DRIVER\",\"staffId\":" + surender
				+ ",\"fromDate\":\"2026-10-12\",\"toDate\":\"2026-10-16\",\"temporary\":true,\"reason\":\"ON_LEAVE\"}")
			.andExpect(status().isCreated());
		assertThat(summaries("VEHICLE", van4)).containsExactly("Driver changed from Jagdish to Surender, 12 to 16 Oct");
		assertThat(jdbc.queryForObject("select changed_by from audit_log where entity_id = ?", Long.class, van4))
			.isEqualTo(office.getId());
		assertThat(jdbc.queryForObject("select details->>'previousStaffName' from audit_log where entity_id = ?",
				String.class, van4))
			.isEqualTo("Jagdish");
		assertThat(jdbc.queryForObject("select details->>'reason' from audit_log where entity_id = ?", String.class,
				van4))
			.isEqualTo("ON_LEAVE");

		// Permanent: from 1 Nov Surender replaces Jagdish for good. Another month, so the day has the year.
		long hari = addStaff("Hari", "DRIVER");
		send(post("/api/v1/vehicles/" + van4 + "/assignments"),
				"{\"duty\":\"DRIVER\",\"staffId\":" + hari + ",\"fromDate\":\"2026-11-01\"}")
			.andExpect(status().isCreated());
		assertThat(summaries("VEHICLE", van4)).last().isEqualTo("Driver changed from Jagdish to Hari from 1 Nov 2026");

		// Nobody before: "set to".
		send(post("/api/v1/vehicles/" + van4 + "/assignments"),
				"{\"duty\":\"ATTENDANT\",\"staffId\":" + balwan + ",\"fromDate\":\"2026-10-07\"}")
			.andExpect(status().isCreated());
		assertThat(summaries("VEHICLE", van4)).last().isEqualTo("Attendant set to Balwan from 7 Oct 2026");
	}

	@Test
	void refusedAssignmentWritesNothing() throws Exception {
		long van4 = addVehicle("Van 4");
		long balwan = addStaff("Balwan", "ATTENDANT");
		int before = auditCount();

		send(post("/api/v1/vehicles/" + van4 + "/assignments"),
				"{\"duty\":\"DRIVER\",\"staffId\":" + balwan + ",\"fromDate\":\"2026-10-07\"}")
			.andExpect(status().isConflict());

		assertThat(auditCount()).isEqualTo(before);
	}

	@Test
	void routeCreateUpdateStopsAndTurnOff() throws Exception {
		long van4 = addVehicle("Van 4");
		long van1 = addVehicle("Van 1");
		long id = idOf(send(post("/api/v1/routes"), "{\"name\":\"Route 4\",\"vehicleId\":" + van4 + "}")
			.andExpect(status().isCreated()));
		assertThat(summaries("ROUTE", id)).containsExactly("Route 4 added on Van 4");

		send(put("/api/v1/routes/" + id), "{\"name\":\"Route 4A\",\"vehicleId\":" + van1 + ",\"active\":true}")
			.andExpect(status().isOk());
		assertThat(summaries("ROUTE", id)).last()
			.isEqualTo("Name changed from Route 4 to Route 4A. Vehicle changed from Van 4 to Van 1.");

		send(put("/api/v1/routes/" + id + "/stops"),
				"[{\"name\":\"Sadhanwas\",\"morningTime\":\"07:25\"},{\"name\":\"Jakhal\"}]")
			.andExpect(status().isOk());
		assertThat(summaries("ROUTE", id)).last().isEqualTo("Stops changed. Now 2: Sadhanwas, Jakhal.");
		assertThat(jdbc.queryForObject("select jsonb_array_length(details->'after') from audit_log "
				+ "where entity_id = ? and summary like 'Stops%'", Integer.class, id))
			.isEqualTo(2);

		// The same list again changes nothing. (The ids are new, so send the saved ones.)
		List<Long> ids = jdbc.queryForList("select id from route_stop where route_id = ? order by seq_no", Long.class,
				id);
		int before = auditCount();
		send(put("/api/v1/routes/" + id + "/stops"),
				"[{\"id\":" + ids.get(0) + ",\"name\":\"Sadhanwas\",\"morningTime\":\"07:25\"},"
						+ "{\"id\":" + ids.get(1) + ",\"name\":\"Jakhal\"}]")
			.andExpect(status().isOk());
		assertThat(auditCount()).isEqualTo(before);

		// Only the order changes: that is a change.
		send(put("/api/v1/routes/" + id + "/stops"),
				"[{\"id\":" + ids.get(1) + ",\"name\":\"Jakhal\"},"
						+ "{\"id\":" + ids.get(0) + ",\"name\":\"Sadhanwas\",\"morningTime\":\"07:25\"}]")
			.andExpect(status().isOk());
		assertThat(summaries("ROUTE", id)).last().isEqualTo("Stops changed. Now 2: Jakhal, Sadhanwas.");

		mockMvc.perform(delete("/api/v1/routes/" + id).header("Authorization", bearer(token)))
			.andExpect(status().isNoContent());
		assertThat(summaries("ROUTE", id)).last().isEqualTo("Turned off.");
	}

}

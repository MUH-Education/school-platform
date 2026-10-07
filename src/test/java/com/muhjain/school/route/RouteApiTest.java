package com.muhjain.school.route;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

/** The "Routes" tests of docs/phases/phase-2-vehicles-staff-routes.md (rules 13 and 14, and rule 2 for vehicles). */
class RouteApiTest extends AbstractIntegrationTest {

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

	private ResultActions send(MockHttpServletRequestBuilder builder, String body) throws Exception {
		return mockMvc.perform(
				builder.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions read(String url) throws Exception {
		return mockMvc.perform(get(url).header("Authorization", bearer(token)));
	}

	private long createRoute(String name, Long vehicleId) throws Exception {
		String body = "{\"name\":\"" + name + "\"" + ((vehicleId != null) ? ",\"vehicleId\":" + vehicleId : "") + "}";
		String json = send(post("/api/v1/routes"), body).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.id")).longValue();
	}

	private ResultActions saveStops(long routeId, String json) throws Exception {
		return send(put("/api/v1/routes/" + routeId + "/stops"), json);
	}

	@Test
	void createSavesTheRouteWithItsVehicle() throws Exception {
		send(post("/api/v1/routes"), "{\"name\":\"Route 4\",\"vehicleId\":" + van4 + "}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.name").value("Route 4"))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.vehicle.id").value(van4))
			.andExpect(jsonPath("$.vehicle.name").value("Van 4"))
			.andExpect(jsonPath("$.vehicle.vehicleType").value("SMALL_VAN"))
			.andExpect(jsonPath("$.vehicle.seats").value(14))
			.andExpect(jsonPath("$.stops.length()").value(0));
	}

	@Test
	void routeMayHaveNoVehicleYet() throws Exception {
		send(post("/api/v1/routes"), "{\"name\":\"Route 9\"}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.vehicle").isEmpty());
	}

	@Test
	void routeNameIsUniqueIgnoringCaseAndSpaces() throws Exception {
		createRoute("Route 4", van4);

		send(post("/api/v1/routes"), "{\"name\":\"route4\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("ROUTE_NAME_ALREADY_USED"));
	}

	@Test
	void vehicleCannotRunTwoActiveRoutes() throws Exception {
		createRoute("Route 4", van4);

		send(post("/api/v1/routes"), "{\"name\":\"Route 5\",\"vehicleId\":" + van4 + "}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("VEHICLE_HAS_ROUTE"))
			.andExpect(jsonPath("$.message").value("Van 4 already runs Route 4."));
		assertThat(jdbc.queryForObject("select count(*) from route", Integer.class)).isEqualTo(1);

		// Moving another route onto Van 4 is refused too.
		long route5 = createRoute("Route 5", van1);
		send(put("/api/v1/routes/" + route5), "{\"name\":\"Route 5\",\"vehicleId\":" + van4 + ",\"active\":true}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("VEHICLE_HAS_ROUTE"));
	}

	@Test
	void vehicleOfATurnedOffRouteCanRunANewRoute() throws Exception {
		long route4 = createRoute("Route 4", van4);
		mockMvc.perform(delete("/api/v1/routes/" + route4).header("Authorization", bearer(token)))
			.andExpect(status().isNoContent());

		createRoute("Route 40", van4);

		// And the old route cannot be turned on again while Van 4 runs Route 40.
		send(put("/api/v1/routes/" + route4), "{\"name\":\"Route 4\",\"vehicleId\":" + van4 + ",\"active\":true}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("VEHICLE_HAS_ROUTE"));
	}

	@Test
	void unknownAndTurnedOffVehiclesAreRefused() throws Exception {
		send(post("/api/v1/routes"), "{\"name\":\"Route 4\",\"vehicleId\":999}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.vehicleId").value("does not exist"));
		jdbc.update("update vehicle set active = false where id = ?", van1);
		send(post("/api/v1/routes"), "{\"name\":\"Route 1\",\"vehicleId\":" + van1 + "}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("VEHICLE_INACTIVE"));
	}

	@Test
	void updateChangesNameAndVehicle() throws Exception {
		long route4 = createRoute("Route 4", van4);

		send(put("/api/v1/routes/" + route4), "{\"name\":\"Route 4A\",\"vehicleId\":" + van1 + ",\"active\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Route 4A"))
			.andExpect(jsonPath("$.vehicle.name").value("Van 1"));
		// A route can lose its vehicle.
		send(put("/api/v1/routes/" + route4), "{\"name\":\"Route 4A\",\"active\":true}").andExpect(status().isOk())
			.andExpect(jsonPath("$.vehicle").isEmpty());
		// Its own name again is fine.
		send(put("/api/v1/routes/" + route4), "{\"name\":\"route 4a\",\"active\":true}").andExpect(status().isOk());
	}

	@Test
	void deleteTurnsTheRouteOffAndKeepsTheRow() throws Exception {
		long route4 = createRoute("Route 4", van4);

		mockMvc.perform(delete("/api/v1/routes/" + route4).header("Authorization", bearer(token)))
			.andExpect(status().isNoContent());

		read("/api/v1/routes/" + route4).andExpect(jsonPath("$.active").value(false));
		mockMvc.perform(delete("/api/v1/routes/" + route4).header("Authorization", bearer(token)))
			.andExpect(status().isNoContent());
	}

	@Test
	void unknownRouteGives404() throws Exception {
		read("/api/v1/routes/999").andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
		saveStops(999, "[]").andExpect(status().isNotFound());
	}

	@Test
	void vehicleWithRouteCannotBeTurnedOff() throws Exception {
		long route4 = createRoute("Route 4", van4);

		mockMvc.perform(delete("/api/v1/vehicles/" + van4).header("Authorization", bearer(token)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("VEHICLE_IN_USE"))
			.andExpect(jsonPath("$.message").value("This vehicle runs Route 4. Move the route first."));
		// PUT with active=false is the same rule.
		send(put("/api/v1/vehicles/" + van4), "{\"name\":\"Van 4\",\"registrationNo\":\"REG VAN 4\","
				+ "\"vehicleType\":\"SMALL_VAN\",\"seats\":14,\"monthlyCost\":30300,\"ownedBy\":\"CONTRACTOR\","
				+ "\"active\":false}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("VEHICLE_IN_USE"));
		assertThat(jdbc.queryForObject("select active from vehicle where id = ?", Boolean.class, van4)).isTrue();

		// Move the route to Van 1. Then Van 4 can be turned off.
		send(put("/api/v1/routes/" + route4), "{\"name\":\"Route 4\",\"vehicleId\":" + van1 + ",\"active\":true}")
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/vehicles/" + van4).header("Authorization", bearer(token)))
			.andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select active from vehicle where id = ?", Boolean.class, van4)).isFalse();
	}

	@Test
	void turnedOffRouteDoesNotHoldTheVehicle() throws Exception {
		long route4 = createRoute("Route 4", van4);
		mockMvc.perform(delete("/api/v1/routes/" + route4).header("Authorization", bearer(token)))
			.andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/v1/vehicles/" + van4).header("Authorization", bearer(token)))
			.andExpect(status().isNoContent());
	}

	@Test
	void vehicleScreensShowTheRoute() throws Exception {
		createRoute("Route 4", van4);

		read("/api/v1/vehicles/" + van4).andExpect(jsonPath("$.route.name").value("Route 4"))
			.andExpect(jsonPath("$.route.id").isNumber());
		read("/api/v1/vehicles").andExpect(jsonPath("$[0].route.name").value("Route 4"))
			.andExpect(jsonPath("$[1].route").isEmpty());
	}

	@Test
	void stopsAreSavedInTheOrderSent() throws Exception {
		long route4 = createRoute("Route 4", van4);

		saveStops(route4, "[{\"name\":\"Sadhanwas\",\"morningTime\":\"07:25\"},"
				+ "{\"name\":\"Jakhal\",\"morningTime\":\"07:40\",\"eveningTime\":\"13:50\"},"
				+ "{\"name\":\"Kanheri\",\"morningTime\":\"07:55\"}]")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stops.length()").value(3))
			.andExpect(jsonPath("$.stops[0].name").value("Sadhanwas"))
			.andExpect(jsonPath("$.stops[0].seqNo").value(1))
			.andExpect(jsonPath("$.stops[0].morningTime").value("07:25"))
			.andExpect(jsonPath("$.stops[0].eveningTime").isEmpty())
			.andExpect(jsonPath("$.stops[1].name").value("Jakhal"))
			.andExpect(jsonPath("$.stops[1].seqNo").value(2))
			.andExpect(jsonPath("$.stops[1].eveningTime").value("13:50"))
			.andExpect(jsonPath("$.stops[2].seqNo").value(3))
			.andExpect(jsonPath("$.stops[2].children").value(0));

		// Order saved in the database.
		assertThat(jdbc.queryForList("select name from route_stop where route_id = ? order by seq_no", String.class,
				route4))
			.containsExactly("Sadhanwas", "Jakhal", "Kanheri");
	}

	@Test
	void reorderingKeepsIdsAddsNewStopsAndRemovesMissingOnes() throws Exception {
		long route4 = createRoute("Route 4", van4);
		saveStops(route4, "[{\"name\":\"Sadhanwas\"},{\"name\":\"Jakhal\"},{\"name\":\"Kanheri\"}]")
			.andExpect(status().isOk());
		Map<String, Long> ids = idsByName(route4);

		// Kanheri first, a new stop second, Sadhanwas third. Jakhal is not in the list, so it is removed.
		saveStops(route4, "[{\"id\":" + ids.get("Kanheri") + ",\"name\":\"Kanheri\",\"morningTime\":\"07:10\"},"
				+ "{\"name\":\"New stop\",\"morningTime\":\"07:48\"},"
				+ "{\"id\":" + ids.get("Sadhanwas") + ",\"name\":\"Sadhanwas village\"}]")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stops.length()").value(3))
			.andExpect(jsonPath("$.stops[0].id").value(ids.get("Kanheri")))
			.andExpect(jsonPath("$.stops[0].seqNo").value(1))
			.andExpect(jsonPath("$.stops[0].morningTime").value("07:10"))
			.andExpect(jsonPath("$.stops[1].name").value("New stop"))
			.andExpect(jsonPath("$.stops[1].seqNo").value(2))
			.andExpect(jsonPath("$.stops[2].id").value(ids.get("Sadhanwas")))
			.andExpect(jsonPath("$.stops[2].name").value("Sadhanwas village"))
			.andExpect(jsonPath("$.stops[2].seqNo").value(3));

		assertThat(jdbc.queryForObject("select count(*) from route_stop where route_id = ?", Integer.class, route4))
			.isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from route_stop where name = 'Jakhal'", Integer.class))
			.isZero();
	}

	@Test
	void swappingTwoStopsWorks() throws Exception {
		long route4 = createRoute("Route 4", van4);
		saveStops(route4, "[{\"name\":\"A\"},{\"name\":\"B\"}]").andExpect(status().isOk());
		Map<String, Long> ids = idsByName(route4);

		saveStops(route4, "[{\"id\":" + ids.get("B") + ",\"name\":\"B\"},{\"id\":" + ids.get("A") + ",\"name\":\"A\"}]")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stops[0].name").value("B"))
			.andExpect(jsonPath("$.stops[1].name").value("A"));
	}

	@Test
	void emptyListRemovesAllStops() throws Exception {
		long route4 = createRoute("Route 4", van4);
		saveStops(route4, "[{\"name\":\"A\"}]").andExpect(status().isOk());

		saveStops(route4, "[]").andExpect(status().isOk()).andExpect(jsonPath("$.stops.length()").value(0));
	}

	@Test
	void badStopListGives400AndSavesNothing() throws Exception {
		long route4 = createRoute("Route 4", van4);
		long other = createRoute("Route 5", van1);
		saveStops(other, "[{\"name\":\"Other stop\"}]").andExpect(status().isOk());
		long otherStopId = idsByName(other).get("Other stop");
		saveStops(route4, "[{\"name\":\"A\"}]").andExpect(status().isOk());
		long ownStopId = idsByName(route4).get("A");

		saveStops(route4, "[{\"name\":\"B\"},{\"name\":\"  \"}]").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields['stops[1].name']").value("must not be blank"));
		// An id of a stop of another route is refused. Never trust an id from the client.
		saveStops(route4, "[{\"id\":" + otherStopId + ",\"name\":\"Stolen\"}]").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields['stops[0].id']").value("is not a stop of this route"));
		saveStops(route4, "[{\"id\":" + ownStopId + ",\"name\":\"A\"},{\"id\":" + ownStopId + ",\"name\":\"A2\"}]")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields['stops[1].id']").value("is in the list twice"));
		saveStops(route4, "[{\"name\":\"Late\",\"morningTime\":\"seven\"}]").andExpect(status().isBadRequest());

		assertThat(idsByName(route4)).containsOnlyKeys("A");
		assertThat(idsByName(other)).containsOnlyKeys("Other stop");
	}

	@Test
	void routeListShowsVehicleAndStopsInOrder() throws Exception {
		long route4 = createRoute("Route 4", van4);
		createRoute("Route 5", null);
		saveStops(route4, "[{\"name\":\"A\"},{\"name\":\"B\"}]").andExpect(status().isOk());

		read("/api/v1/routes").andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].name").value("Route 4"))
			.andExpect(jsonPath("$[0].vehicle.name").value("Van 4"))
			.andExpect(jsonPath("$[0].stops[1].name").value("B"))
			.andExpect(jsonPath("$[1].vehicle").isEmpty())
			.andExpect(jsonPath("$[1].stops.length()").value(0));
		read("/api/v1/routes/" + route4).andExpect(jsonPath("$.stops[0].children").value(0));
	}

	@Test
	void transportInchargeCanEditButOfficeAdminOnlyReads() throws Exception {
		String adminToken = tokenFor(addUser("+919812340004", Role.OFFICE_ADMIN));

		mockMvc.perform(get("/api/v1/routes").header("Authorization", bearer(adminToken))).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/routes").header("Authorization", bearer(adminToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Route 7\"}"))
			.andExpect(status().isForbidden());
	}

	private Map<String, Long> idsByName(long routeId) {
		List<Map<String, Object>> rows = jdbc.queryForList("select id, name from route_stop where route_id = ?",
				routeId);
		return rows.stream()
			.collect(java.util.stream.Collectors.toMap(r -> (String) r.get("name"), r -> ((Number) r.get("id")).longValue()));
	}

}

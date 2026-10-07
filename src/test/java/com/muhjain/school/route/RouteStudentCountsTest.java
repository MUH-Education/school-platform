package com.muhjain.school.route;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rule 15 and the "children" rules, with children pretended. There are no students before Phase 3, so
 * {@link StudentCounts} is replaced here by a mock. Example: 6 children board at Jakhal.
 */
class RouteStudentCountsTest extends AbstractIntegrationTest {

	@MockitoBean
	private StudentCounts studentCounts;

	private String token;

	private long route4;

	private long jakhal;

	private long kanheri;

	@BeforeEach
	void addRouteWithStops() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		token = tokenFor(addUser("+919812340003", Role.TRANSPORT_INCHARGE));
		long van4 = addVehicle("Van 4");
		route4 = jdbc.queryForObject("insert into route (name, vehicle_id) values ('Route 4', ?) returning id",
				Long.class, van4);
		jakhal = jdbc.queryForObject("insert into route_stop (route_id, name, seq_no) values (?, 'Jakhal', 1) "
				+ "returning id", Long.class, route4);
		kanheri = jdbc.queryForObject("insert into route_stop (route_id, name, seq_no) values (?, 'Kanheri', 2) "
				+ "returning id", Long.class, route4);
		when(studentCounts.childrenByStop(eq(route4), any(LocalDate.class))).thenReturn(Map.of(jakhal, 6));
		when(studentCounts.childrenOnRoute(eq(route4), any(LocalDate.class))).thenReturn(6);
	}

	@Test
	void stopWhereChildrenBoardCannotBeRemoved() throws Exception {
		mockMvc.perform(put("/api/v1/routes/" + route4 + "/stops").header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("[{\"id\":" + kanheri + ",\"name\":\"Kanheri\"}]"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("STOP_HAS_STUDENTS"))
			.andExpect(jsonPath("$.message").value("6 children board at Jakhal. Move them to another stop first."));

		// Nothing changed.
		assertThat(jdbc.queryForObject("select count(*) from route_stop where route_id = ?", Integer.class, route4))
			.isEqualTo(2);
	}

	@Test
	void stopWithNoChildrenCanBeRemovedAndStopWithChildrenCanBeRenamedOrMoved() throws Exception {
		// Kanheri has no children: removed. Jakhal stays (renamed).
		mockMvc.perform(put("/api/v1/routes/" + route4 + "/stops").header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("[{\"id\":" + jakhal + ",\"name\":\"Jakhal Mandi\"}]"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stops.length()").value(1))
			.andExpect(jsonPath("$.stops[0].name").value("Jakhal Mandi"))
			.andExpect(jsonPath("$.stops[0].children").value(6));
	}

	@Test
	void routeWithChildrenCannotBeTurnedOff() throws Exception {
		mockMvc.perform(delete("/api/v1/routes/" + route4).header("Authorization", bearer(token)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("ROUTE_HAS_STUDENTS"))
			.andExpect(jsonPath("$.message").value("6 children ride on Route 4. Move them to another route first."));
		mockMvc.perform(put("/api/v1/routes/" + route4).header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Route 4\",\"active\":false}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("ROUTE_HAS_STUDENTS"));

		assertThat(jdbc.queryForObject("select active from route where id = ?", Boolean.class, route4)).isTrue();
	}

	@Test
	void routeDetailShowsChildrenPerStop() throws Exception {
		mockMvc.perform(get("/api/v1/routes/" + route4).header("Authorization", bearer(token)))
			.andExpect(jsonPath("$.stops[0].name").value("Jakhal"))
			.andExpect(jsonPath("$.stops[0].children").value(6))
			.andExpect(jsonPath("$.stops[1].name").value("Kanheri"))
			.andExpect(jsonPath("$.stops[1].children").value(0));
	}

}

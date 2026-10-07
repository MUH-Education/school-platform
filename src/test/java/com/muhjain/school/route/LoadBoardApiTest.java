package com.muhjain.school.route;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /routes/load-board} with children pretended (there are no students before Phase 3).
 * Fleet: Van 4, Van 5, Van 7 (14 seats each), Bus 8 (26 seats, runs no route), Van 9 (turned off).
 * Routes: Route 4 (Van 4, 19 children), Route 5 (Van 5, 6), Route 6 (no vehicle, 3), Route 7 (Van 7, 0),
 * Route 10 (turned off). Settings: 11 months, fee 8800, 95 percent.
 */
class LoadBoardApiTest extends AbstractIntegrationTest {

	@MockitoBean
	private StudentCounts studentCounts;

	private String token;

	private long route4;

	@BeforeEach
	void addFleet() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		token = tokenFor(addUser("+919812340003", Role.TRANSPORT_INCHARGE));
		long van4 = addVehicle("Van 4");
		long van5 = addVehicle("Van 5");
		long van7 = addVehicle("Van 7");
		addVehicle("Bus 8", 26);
		long van9 = addVehicle("Van 9");
		jdbc.update("update vehicle set active = false where id = ?", van9);
		route4 = addRoute("Route 4", van4, true);
		long route5 = addRoute("Route 5", van5, true);
		long route6 = addRoute("Route 6", null, true);
		long route7 = addRoute("Route 7", van7, true);
		addRoute("Route 10", van9, false);
		when(studentCounts.childrenOnRoute(anyLong(), any(LocalDate.class))).thenReturn(0);
		when(studentCounts.childrenOnRoute(route4, LocalDate.of(2026, 10, 7))).thenReturn(19);
		when(studentCounts.childrenOnRoute(route5, LocalDate.of(2026, 10, 7))).thenReturn(6);
		when(studentCounts.childrenOnRoute(route6, LocalDate.of(2026, 10, 7))).thenReturn(3);
		when(studentCounts.childrenOnRoute(route7, LocalDate.of(2026, 10, 7))).thenReturn(0);
		when(studentCounts.childrenByStop(anyLong(), any(LocalDate.class))).thenReturn(Map.of());
	}

	private long addRoute(String name, Long vehicleId, boolean active) {
		return jdbc.queryForObject("insert into route (name, vehicle_id, active) values (?, ?, ?) returning id",
				Long.class, name, vehicleId, active);
	}

	private ResultActions board() throws Exception {
		return mockMvc.perform(get("/api/v1/routes/load-board").header("Authorization", bearer(token)));
	}

	@Test
	void route4RowMatchesTheExampleInTheDocs() throws Exception {
		board().andExpect(status().isOk())
			.andExpect(jsonPath("$.routes[0].routeId").value(route4))
			.andExpect(jsonPath("$.routes[0].name").value("Route 4"))
			.andExpect(jsonPath("$.routes[0].vehicle").value("Van 4"))
			.andExpect(jsonPath("$.routes[0].vehicleType").value("SMALL_VAN"))
			.andExpect(jsonPath("$.routes[0].seats").value(14))
			.andExpect(jsonPath("$.routes[0].children").value(19))
			.andExpect(jsonPath("$.routes[0].load").value(1.36))
			.andExpect(jsonPath("$.routes[0].overBy").value(5))
			.andExpect(jsonPath("$.routes[0].spare").value(0))
			.andExpect(jsonPath("$.routes[0].yearlyCost").value(333300.00))
			.andExpect(jsonPath("$.routes[0].costPerChild").value(17542.11))
			.andExpect(jsonPath("$.routes[0].feeGot").value(158840.00))
			.andExpect(jsonPath("$.routes[0].surplus").value(-174460.00))
			.andExpect(jsonPath("$.routes[0].verdict").value("OVER"));
	}

	@Test
	void onlyActiveRoutesAreListedAndEachHasItsVerdict() throws Exception {
		board().andExpect(jsonPath("$.routes.length()").value(4))
			.andExpect(jsonPath("$.routes[1].name").value("Route 5"))
			.andExpect(jsonPath("$.routes[1].verdict").value("THIN"))
			.andExpect(jsonPath("$.routes[2].name").value("Route 6"))
			.andExpect(jsonPath("$.routes[2].verdict").value("NO_VEHICLE"))
			.andExpect(jsonPath("$.routes[2].vehicle").isEmpty())
			.andExpect(jsonPath("$.routes[2].seats").isEmpty())
			.andExpect(jsonPath("$.routes[2].load").isEmpty())
			.andExpect(jsonPath("$.routes[2].feeGot").value(25080.00))
			.andExpect(jsonPath("$.routes[3].name").value("Route 7"))
			.andExpect(jsonPath("$.routes[3].verdict").value("NO_CHILDREN"))
			.andExpect(jsonPath("$.routes[3].spare").value(14))
			.andExpect(jsonPath("$.routes[3].costPerChild").isEmpty());
	}

	@Test
	void totalsCoverTheWholeFleetIncludingTheVehicleWithNoRoute() throws Exception {
		// Fleet = Van 4, Van 5, Van 7, Bus 8. Van 9 is turned off, so it is not counted.
		board().andExpect(jsonPath("$.totals.routes").value(4))
			.andExpect(jsonPath("$.totals.vehicles").value(4))
			.andExpect(jsonPath("$.totals.seats").value(68))
			.andExpect(jsonPath("$.totals.children").value(28))
			.andExpect(jsonPath("$.totals.load").value(0.41))
			.andExpect(jsonPath("$.totals.yearlyCost").value(1333200.00))
			.andExpect(jsonPath("$.totals.costPerChild").value(47614.29))
			.andExpect(jsonPath("$.totals.feeGot").value(234080.00))
			.andExpect(jsonPath("$.totals.surplus").value(-1099120.00));
	}

	@Test
	void settingsChangeTheNumbers() throws Exception {
		jdbc.update("update app_setting set value = '10' where key = 'transport.months_operated'");
		jdbc.update("update app_setting set value = '9000' where key = 'transport.bus_fee_per_year'");
		jdbc.update("update app_setting set value = '100' where key = 'transport.collection_pct'");
		try {
			// Route 4: yearly cost 303,000.00. Fee got 19 x 9000 = 171,000.00.
			board().andExpect(jsonPath("$.routes[0].yearlyCost").value(303000.00))
				.andExpect(jsonPath("$.routes[0].feeGot").value(171000.00))
				.andExpect(jsonPath("$.routes[0].surplus").value(-132000.00));
		}
		finally {
			jdbc.update("update app_setting set value = '11' where key = 'transport.months_operated'");
			jdbc.update("update app_setting set value = '8800' where key = 'transport.bus_fee_per_year'");
			jdbc.update("update app_setting set value = '95' where key = 'transport.collection_pct'");
		}
	}

	@Test
	void officeAdminMayReadButTheAdmissionsDeskMayNot() throws Exception {
		String adminToken = tokenFor(addUser("+919812340004", Role.OFFICE_ADMIN));
		String deskToken = tokenFor(addUser("+919812340005", Role.ADMISSIONS_DESK));

		mockMvc.perform(get("/api/v1/routes/load-board").header("Authorization", bearer(adminToken)))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/routes/load-board").header("Authorization", bearer(deskToken)))
			.andExpect(status().isForbidden());
	}

}

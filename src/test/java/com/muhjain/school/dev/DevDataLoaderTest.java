package com.muhjain.school.dev;

import java.time.Instant;
import java.util.List;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The loader, run in a context with the {@code dev} profile on. The test cleans the tables before each test, so
 * the loader is called by hand here. Today is 7 Oct 2026.
 */
@ActiveProfiles({ "test", "dev" })
class DevDataLoaderTest extends AbstractIntegrationTest {

	@Autowired
	private DevDataLoader loader;

	private String token;

	@BeforeEach
	void load() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		token = tokenFor(addUser("+919812340001", Role.OWNER));
		loader.run(null);
	}

	private ResultActions read(String url) throws Exception {
		return mockMvc.perform(get(url).header("Authorization", bearer(token)));
	}

	@Test
	void loadsNineVehiclesWithTheRightSizeAndCost() {
		assertThat(jdbc.queryForObject("select count(*) from vehicle", Integer.class)).isEqualTo(9);
		assertThat(jdbc.queryForObject("select count(*) from vehicle where vehicle_type = 'SMALL_VAN' "
				+ "and seats = 14", Integer.class))
			.isEqualTo(7);
		assertThat(jdbc.queryForObject("select count(*) from vehicle where vehicle_type = 'MID_BUS' "
				+ "and seats = 26", Integer.class))
			.isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from vehicle where monthly_cost = 30300.00",
				Integer.class))
			.isEqualTo(9);
		assertThat(jdbc.queryForObject("select sum(seats) from vehicle", Integer.class)).isEqualTo(150);
	}

	@Test
	void loadBoardShowsNineRoutesAnd150Seats() throws Exception {
		read("/api/v1/routes/load-board").andExpect(status().isOk())
			.andExpect(jsonPath("$.routes.length()").value(9))
			.andExpect(jsonPath("$.totals.routes").value(9))
			.andExpect(jsonPath("$.totals.vehicles").value(9))
			.andExpect(jsonPath("$.totals.seats").value(150))
			// Phase 3: 40 students, 35 with a bus. One of them starts the bus 25 days from now, so 34 ride today.
			.andExpect(jsonPath("$.totals.children").value(34));
	}

	@Test
	void everyRouteRunsItsOwnVehicleAndHasOrderedStops() throws Exception {
		read("/api/v1/routes").andExpect(jsonPath("$.length()").value(9))
			.andExpect(jsonPath("$[3].name").value("Route 4"))
			.andExpect(jsonPath("$[3].vehicle.name").value("Van 4"))
			.andExpect(jsonPath("$[3].stops.length()").value(4))
			.andExpect(jsonPath("$[3].stops[0].name").value("Sadhanwas"))
			.andExpect(jsonPath("$[3].stops[0].seqNo").value(1))
			.andExpect(jsonPath("$[3].stops[0].morningTime").value("07:10"))
			.andExpect(jsonPath("$[3].stops[1].morningTime").value("07:22"))
			.andExpect(jsonPath("$[8].vehicle.name").value("Bus 9"))
			.andExpect(jsonPath("$[8].vehicle.seats").value(26))
			.andExpect(jsonPath("$[8].stops[0].name").value("Model Town"));
	}

	@Test
	void everyVehicleHasADriverAndAnAttendantToday() throws Exception {
		read("/api/v1/vehicles").andExpect(jsonPath("$.length()").value(9))
			.andExpect(jsonPath("$[3].name").value("Van 4"))
			.andExpect(jsonPath("$[3].registrationNo").value("HR 23 A 1104"))
			.andExpect(jsonPath("$[3].driver.name").value("Jagdish"))
			.andExpect(jsonPath("$[3].attendant.name").value("Balwan"))
			.andExpect(jsonPath("$[3].route.name").value("Route 4"))
			.andExpect(jsonPath("$[0].driver.name").value("Rajpal"));
		assertThat(jdbc.queryForObject("select count(*) from vehicle_assignment", Integer.class)).isEqualTo(18);
	}

	@Test
	void spareDriverAndAttendantAreOnNoVehicle() throws Exception {
		assertThat(jdbc.queryForObject("select count(*) from staff", Integer.class)).isEqualTo(20);
		read("/api/v1/staff").andExpect(jsonPath("$[18].name").value("Surender"))
			.andExpect(jsonPath("$[18].worksOn").isEmpty())
			.andExpect(jsonPath("$[19].name").value("Naresh"))
			.andExpect(jsonPath("$[19].worksOn").isEmpty());
	}

	@Test
	void attentionListIsNotEmpty() throws Exception {
		read("/api/v1/vehicles/attention").andExpect(jsonPath("$.length()").value(3))
			// Van 6 PUC ended 5 days ago, Van 2 insurance ends in 12 days, Dalbir's licence in 20 days.
			.andExpect(jsonPath("$[0].vehicleName").value("Van 6"))
			.andExpect(jsonPath("$[0].status").value("ENDED"))
			.andExpect(jsonPath("$[1].vehicleName").value("Van 2"))
			.andExpect(jsonPath("$[1].daysLeft").value(12))
			.andExpect(jsonPath("$[2].staffName").value("Dalbir"))
			.andExpect(jsonPath("$[2].daysLeft").value(20));
	}

	@Test
	void runningItAgainAddsNothing() {
		loader.run(null);

		assertThat(jdbc.queryForObject("select count(*) from vehicle", Integer.class)).isEqualTo(9);
		assertThat(jdbc.queryForObject("select count(*) from staff", Integer.class)).isEqualTo(20);
		assertThat(jdbc.queryForObject("select count(*) from route", Integer.class)).isEqualTo(9);
	}

	@Test
	void everythingLoadedIsInTheChangeHistory() {
		List<String> lines = jdbc.queryForList("select summary from audit_log where entity_type = 'VEHICLE' "
				+ "order by id limit 3", String.class);

		assertThat(lines.getFirst()).isEqualTo("Vehicle Van 1 (HR 23 A 1101) added");
	}

	@Test
	void loaderIsOnlyForTheDevProfile() {
		Profile profile = DevDataLoader.class.getAnnotation(Profile.class);

		assertThat(profile).isNotNull();
		assertThat(profile.value()).containsExactly("dev");
	}

}

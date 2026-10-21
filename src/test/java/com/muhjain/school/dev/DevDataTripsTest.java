package com.muhjain.school.dev;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.service.dev.DevDataLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 4.12: with the dev data, Bus status at the fixed time 7:48 shows 9 routes with the states of the design.
 * {@code app.bootstrap.owner-phone} is set so this context's automatic startup run of the loader has a real
 * OWNER to use as {@code created_by} — see {@link DevDataLoaderTest}.
 */
@ActiveProfiles({ "test", "dev" })
@TestPropertySource(properties = "app.bootstrap.owner-phone=9999900000")
class DevDataTripsTest extends AbstractIntegrationTest {

	@Autowired
	private DevDataLoader loader;

	private String token;

	@BeforeEach
	void load() {
		clock.setInstant(Instant.parse("2026-10-07T02:18:00Z")); // 7:48 school time
		token = tokenFor(addUser("+919812340001", Role.OWNER));
		loader.run(null);
	}

	private org.springframework.test.web.servlet.ResultActions read(String url) throws Exception {
		return mockMvc.perform(get(url).header("Authorization", bearer(token)));
	}

	@Test
	void busStatusAt0748ShowsNineRoutesWithTheDesignStates() throws Exception {
		read("/api/v1/bus-status").andExpect(status().isOk())
			.andExpect(jsonPath("$.date").value("2026-10-07"))
			.andExpect(jsonPath("$.phase").value("MORNING"))
			.andExpect(jsonPath("$.routes.length()").value(9))
			.andExpect(jsonPath("$.routes[0].state").value("ON_THE_WAY"))
			.andExpect(jsonPath("$.routes[1].state").value("REACHED_SCHOOL"))
			.andExpect(jsonPath("$.routes[1].school.reachedAt").value("07:46"))
			.andExpect(jsonPath("$.routes[2].state").value("NO_TAPS"))
			.andExpect(jsonPath("$.routes[2].lateMinutes").value(33))
			.andExpect(jsonPath("$.routes[3].state").value("LATE"))
			.andExpect(jsonPath("$.routes[4].state").value("LATE"))
			.andExpect(jsonPath("$.routes[4].lateMinutes").value(16))
			.andExpect(jsonPath("$.routes[5].state").value("ON_THE_WAY"))
			.andExpect(jsonPath("$.routes[6].state").value("NOT_STARTED"))
			.andExpect(jsonPath("$.routes[7].state").value("ON_THE_WAY"))
			.andExpect(jsonPath("$.routes[8].state").value("NOT_STARTED"));
	}

	@Test
	void attentionListShowsTheNoTapsAndLateRoutes() throws Exception {
		read("/api/v1/bus-status/attention")
			.andExpect(jsonPath("$[?(@.kind=='NO_TAPS' || @.kind=='LATE')]", hasSize(3)))
			.andExpect(jsonPath("$[?(@.title=='Route 3 has no taps yet')]").exists())
			.andExpect(jsonPath("$[?(@.title=='Route 4 is late')]").exists())
			.andExpect(jsonPath("$[?(@.title=='Route 5 is late')]").exists());
	}

	@Test
	void runningItAgainAddsNoTaps() {
		int before = jdbc.queryForObject("select count(*) from boarding_event", Integer.class);
		assertThat(before).isGreaterThan(10);
		loader.run(null);
		assertThat(jdbc.queryForObject("select count(*) from boarding_event", Integer.class)).isEqualTo(before);
	}

}

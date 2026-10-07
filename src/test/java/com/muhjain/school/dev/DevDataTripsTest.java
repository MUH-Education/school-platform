package com.muhjain.school.dev;

import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Task 4.12: with the dev data, Bus status at the fixed time 7:48 shows 9 routes with the states of the design. */
@ActiveProfiles({ "test", "dev" })
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
			.andExpect(jsonPath("$.length()").value(9))
			.andExpect(jsonPath("$[0].state").value("ON_THE_WAY"))
			.andExpect(jsonPath("$[1].state").value("REACHED_SCHOOL"))
			.andExpect(jsonPath("$[1].reachedAt").value("07:46"))
			.andExpect(jsonPath("$[2].state").value("NO_TAPS"))
			.andExpect(jsonPath("$[2].lateMinutes").value(33))
			.andExpect(jsonPath("$[3].state").value("LATE"))
			.andExpect(jsonPath("$[4].state").value("LATE"))
			.andExpect(jsonPath("$[4].lateMinutes").value(16))
			.andExpect(jsonPath("$[5].state").value("ON_THE_WAY"))
			.andExpect(jsonPath("$[6].state").value("NOT_STARTED"))
			.andExpect(jsonPath("$[7].state").value("ON_THE_WAY"))
			.andExpect(jsonPath("$[8].state").value("NOT_STARTED"));
	}

	@Test
	void attentionListShowsTheNoTapsAndLateRoutes() throws Exception {
		read("/api/v1/bus-status/attention").andExpect(jsonPath("$.routes.length()").value(3))
			.andExpect(jsonPath("$.routes[?(@.name=='Route 3')].state").value("NO_TAPS"))
			.andExpect(jsonPath("$.routes[?(@.name=='Route 4')].state").value("LATE"))
			.andExpect(jsonPath("$.routes[?(@.name=='Route 5')].state").value("LATE"));
	}

	@Test
	void runningItAgainAddsNoTaps() {
		int before = jdbc.queryForObject("select count(*) from boarding_event", Integer.class);
		assertThat(before).isGreaterThan(10);
		loader.run(null);
		assertThat(jdbc.queryForObject("select count(*) from boarding_event", Integer.class)).isEqualTo(before);
	}

}

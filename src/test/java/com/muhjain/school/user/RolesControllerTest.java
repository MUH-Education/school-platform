package com.muhjain.school.user;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RolesControllerTest extends AbstractIntegrationTest {

	@Test
	void ownerGetsTheWholeTable() throws Exception {
		String token = tokenFor(addUser("+919812340001", Role.OWNER));

		mockMvc.perform(get("/api/v1/roles").header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions.length()").value(19))
			.andExpect(jsonPath("$.permissions[0]").value("BUS_STATUS_VIEW"))
			.andExpect(jsonPath("$.roles.length()").value(5))
			.andExpect(jsonPath("$.roles[0].role").value("OWNER"))
			.andExpect(jsonPath("$.roles[0].permissions.length()").value(19))
			.andExpect(jsonPath("$.roles[4].role").value("ATTENDANT"))
			.andExpect(jsonPath("$.roles[4].permissions[0]").value("TRIPS_RECORD"));
	}

}

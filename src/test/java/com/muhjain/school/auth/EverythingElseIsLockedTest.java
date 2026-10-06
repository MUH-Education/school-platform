package com.muhjain.school.auth;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EverythingElseIsLockedTest extends AbstractIntegrationTest {

	@Test
	void getWithoutTokenIs401() throws Exception {
		mockMvc.perform(get("/api/v1/anything"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("UNAUTHENTICATED"))
			.andExpect(jsonPath("$.message").isNotEmpty())
			.andExpect(jsonPath("$.fields").value(nullValue()));
	}

	@Test
	void postWithoutTokenIs401() throws Exception {
		mockMvc.perform(post("/api/v1/anything"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
	}

}

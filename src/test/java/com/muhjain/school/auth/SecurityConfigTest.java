package com.muhjain.school.auth;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityConfigTest extends AbstractIntegrationTest {

	@Test
	void otpUrlsAreOpen() throws Exception {
		mockMvc.perform(post("/api/v1/auth/otp/request"))
			.andExpect(status().is(not(401)))
			.andExpect(status().is(not(403)));
		mockMvc.perform(post("/api/v1/auth/otp/verify"))
			.andExpect(status().is(not(401)))
			.andExpect(status().is(not(403)));
	}

	@Test
	void healthPartsAreOpen() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
	}

	@Test
	void badTokenGives401InOurFormat() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-real-token"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("WWW-Authenticate", "Bearer"))
			.andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
	}

	@Test
	void tokenSignedWithAnotherSecretGives401() throws Exception {
		// HS256 token signed with "another-secret-another-secret-123"
		String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6Ik9XTkVSIiwidmVyIjowLCJleHAiOjQxMDI0NDQ4MDB9."
				+ "3lF3n9Qm8m3m2b0u7N0c4kq8Vt0bq0m2vS0k4Yy9cXs";
		mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
	}

}

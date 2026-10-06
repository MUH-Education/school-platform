package com.muhjain.school.auth;

import java.time.Duration;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The "Token" tests of docs/phases/phase-1-login-users.md. */
class TokenTest extends AbstractIntegrationTest {

	private static final String PHONE = "+919812340002";

	@Test
	void noTokenGives401() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
	}

	@Test
	void tokenOfTurnedOffUserGives401() throws Exception {
		AppUser user = addUser(PHONE, Role.OFFICE_ADMIN);
		String token = login(PHONE);
		me(token).andExpect(status().isOk());

		user = userRepository.findById(user.getId()).orElseThrow();
		user.setActive(false);
		userRepository.saveAndFlush(user);

		me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
	}

	@Test
	void oldTokenAfterLogoutGives401() throws Exception {
		addUser(PHONE, Role.OFFICE_ADMIN);
		String phoneToken = login(PHONE);
		String laptopToken = login(PHONE);

		mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(laptopToken)))
			.andExpect(status().isNoContent());

		me(laptopToken).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
		me(phoneToken).andExpect(status().isUnauthorized());
		me(login(PHONE)).andExpect(status().isOk());
	}

	@Test
	void expiredTokenGives401() throws Exception {
		AppUser user = addUser(PHONE, Role.OFFICE_ADMIN);
		String token = tokenFor(user);
		me(token).andExpect(status().isOk());

		clock.advance(Duration.ofDays(31));

		me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
	}

	@Test
	void meShowsTheLoggedInUser() throws Exception {
		AppUser user = addUser(PHONE, Role.ATTENDANT);

		me(tokenFor(user)).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(user.getId()))
			.andExpect(jsonPath("$.role").value("ATTENDANT"))
			.andExpect(jsonPath("$.permissions.length()").value(1))
			.andExpect(jsonPath("$.permissions[0]").value("TRIPS_RECORD"));
	}

	private org.springframework.test.web.servlet.ResultActions me(String token) throws Exception {
		return mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)));
	}

}

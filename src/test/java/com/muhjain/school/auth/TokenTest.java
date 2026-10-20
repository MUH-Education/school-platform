package com.muhjain.school.auth;

import java.time.Duration;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
		AppUser owner = addUser("+919812340001", Role.OWNER);
		AppUser user = addUser(PHONE, Role.OFFICE_ADMIN);
		String token = login(PHONE);
		me(token).andExpect(status().isOk());

		mockMvc
			.perform(put("/api/v1/users/" + user.getId()).header("Authorization", bearer(tokenFor(owner)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + PHONE + "\",\"role\":\"OFFICE_ADMIN\",\"active\":false}"))
			.andExpect(status().isOk());

		me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
	}

	@Test
	void logoutEndsOnlyThatOneSessionNotEveryDevice() throws Exception {
		addUser(PHONE, Role.OFFICE_ADMIN);
		String phoneToken = login(PHONE);
		String laptopToken = login(PHONE);

		mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(laptopToken)))
			.andExpect(status().isNoContent());

		me(laptopToken).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
		// The phone was never logged out: it is a different session.
		me(phoneToken).andExpect(status().isOk());
	}

	@Test
	void roleChangeTakesEffectOnNextRequest() throws Exception {
		AppUser owner = addUser("+919812340001", Role.OWNER);
		AppUser neelam = addUser(PHONE, Role.ADMISSIONS_DESK);
		String oldToken = login(PHONE);
		mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(oldToken)))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(put("/api/v1/users/" + neelam.getId()).header("Authorization", bearer(tokenFor(owner)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + PHONE + "\",\"role\":\"OWNER\",\"active\":true}"))
			.andExpect(status().isOk());

		// Rule 6: the old session ends at once. After a new login the new role works.
		mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(oldToken)))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(login(PHONE))))
			.andExpect(status().isOk());
	}

	@Test
	void idleSessionExpiresAfterFifteenMinutes() throws Exception {
		AppUser user = addUser(PHONE, Role.OFFICE_ADMIN);
		String token = tokenFor(user);
		me(token).andExpect(status().isOk());

		clock.advance(Duration.ofMinutes(16));

		me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
	}

	@Test
	void activeSessionSlidesAndNeverGoesPastTheTwelveHourCap() throws Exception {
		AppUser user = addUser(PHONE, Role.OFFICE_ADMIN);
		String token = tokenFor(user);

		// A request every 10 minutes (inside the 15-minute idle timeout) keeps the session alive
		// well past what a single, un-refreshed 15-minute window would allow.
		for (int i = 0; i < 50; i++) {
			clock.advance(Duration.ofMinutes(10));
			me(token).andExpect(status().isOk());
		}

		// But it can never outlive the 12-hour hard cap from login, no matter how active it stayed.
		clock.advance(Duration.ofHours(12));
		me(token).andExpect(status().isUnauthorized());
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

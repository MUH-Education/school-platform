package com.muhjain.school.auth;

import java.time.Duration;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The "Login" tests of docs/phases/phase-1-login-users.md, over HTTP. */
class OtpLoginTest extends AbstractIntegrationTest {

	private static final String PHONE = "+919812340002";

	@Test
	void unknownAndKnownPhoneGetTheSameAnswer() throws Exception {
		addUser(PHONE, Role.OFFICE_ADMIN);

		String known = request("98123 40002").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String unknown = request("98123 49999").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(known).isEqualTo(unknown)
			.isEqualTo("{\"message\":\"If this number is registered, a code has been sent.\","
					+ "\"expiresInSeconds\":300,\"resendAfterSeconds\":60}");
	}

	@Test
	void onlyKnownPhoneCreatesAnOtpRow() throws Exception {
		addUser(PHONE, Role.OFFICE_ADMIN);

		request("98123 49999").andExpect(status().isOk());
		assertThat(otpRows()).isZero();

		request("98123 40002").andExpect(status().isOk());
		assertThat(otpRows()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select request_ip from otp_code", String.class)).isEqualTo("127.0.0.1");
	}

	@Test
	void rightCodeGivesAToken() throws Exception {
		AppUser user = addUser(PHONE, Role.OFFICE_ADMIN);
		request(PHONE);

		verify(PHONE, lastCodeSentTo(PHONE)).andExpect(status().isOk())
			.andExpect(jsonPath("$.token").isNotEmpty())
			.andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.endsWith("+05:30")))
			.andExpect(jsonPath("$.user.id").value(user.getId()))
			.andExpect(jsonPath("$.user.phone").value(PHONE))
			.andExpect(jsonPath("$.user.role").value("OFFICE_ADMIN"))
			.andExpect(jsonPath("$.user.permissions[0]").value("BUS_STATUS_VIEW"))
			.andExpect(jsonPath("$.user.route").isEmpty());
	}

	@Test
	void sameCodeCannotBeUsedTwice() throws Exception {
		addUser(PHONE, Role.OFFICE_ADMIN);
		request(PHONE);
		String code = lastCodeSentTo(PHONE);

		verify(PHONE, code).andExpect(status().isOk());
		verify(PHONE, code).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("OTP_INVALID"));
	}

	@Test
	void codeOlderThanFiveMinutesIsRejected() throws Exception {
		addUser(PHONE, Role.OFFICE_ADMIN);
		request(PHONE);
		clock.advance(Duration.ofMinutes(5).plusSeconds(1));

		verify(PHONE, lastCodeSentTo(PHONE)).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("OTP_INVALID"));
	}

	@Test
	void fiveWrongTriesLockTheCode() throws Exception {
		addUser(PHONE, Role.OFFICE_ADMIN);
		request(PHONE);
		String right = lastCodeSentTo(PHONE);
		String wrong = right.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 5; i++) {
			verify(PHONE, wrong).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("OTP_INVALID"));
		}
		verify(PHONE, wrong).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").value("OTP_LOCKED"));
		verify(PHONE, right).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").value("OTP_LOCKED"));
	}

	@Test
	void secondRequestWithinSixtySecondsIsRejected() throws Exception {
		addUser(PHONE, Role.OFFICE_ADMIN);
		request(PHONE).andExpect(status().isOk());
		clock.advance(Duration.ofSeconds(59));

		request(PHONE).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.error").value("OTP_TOO_MANY_REQUESTS"))
			.andExpect(header().string("Retry-After", "1"))
			.andExpect(jsonPath("$.fields").isEmpty());
	}

	@Test
	void sixthRequestInOneHourIsRejected() throws Exception {
		addUser(PHONE, Role.OFFICE_ADMIN);
		for (int i = 0; i < 5; i++) {
			request(PHONE).andExpect(status().isOk());
			clock.advance(Duration.ofMinutes(2));
		}

		// The first code was made 10 minutes ago, so the phone may ask again in 50 minutes.
		request(PHONE).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.error").value("OTP_TOO_MANY_REQUESTS"))
			.andExpect(header().string("Retry-After", String.valueOf(50 * 60)));
		assertThat(otpRows()).isEqualTo(5);

		clock.advance(Duration.ofMinutes(50).plusSeconds(1));
		request(PHONE).andExpect(status().isOk());
	}

	@Test
	void otpIsNeverInAnyResponseBody() throws Exception {
		addUser(PHONE, Role.OFFICE_ADMIN);
		String requestBody = request(PHONE).andReturn().getResponse().getContentAsString();
		String code = lastCodeSentTo(PHONE);
		String wrong = code.equals("000000") ? "111111" : "000000";
		String wrongBody = verify(PHONE, wrong).andReturn().getResponse().getContentAsString();
		String loginBody = verify(PHONE, code).andReturn().getResponse().getContentAsString();
		String token = com.jayway.jsonpath.JsonPath.read(loginBody, "$.token");
		String meBody = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.get("/api/v1/auth/me")
			.header("Authorization", bearer(token))).andReturn().getResponse().getContentAsString();

		String loginWithoutToken = loginBody.replace(token, "");
		for (String body : new String[] { requestBody, wrongBody, loginWithoutToken, meBody }) {
			assertThat(body).doesNotContain(code);
		}
	}

	@Test
	void turnedOffUserCannotRequestOrVerify() throws Exception {
		AppUser user = addUser(PHONE, Role.OFFICE_ADMIN);
		request(PHONE);
		String code = lastCodeSentTo(PHONE);
		user.setActive(false);
		userRepository.saveAndFlush(user);
		clock.advance(Duration.ofMinutes(1));

		request(PHONE).andExpect(status().isOk());
		assertThat(otpRows()).as("no new code for a turned-off user").isEqualTo(1);
		verify(PHONE, code).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("OTP_INVALID"));
	}

	@Test
	void badInputIsValidation() throws Exception {
		request("12345").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION"));
		verify(PHONE, "12ab").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$.fields.otp").value("must be 6 digits"));
	}

	private ResultActions request(String phone) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"" + phone + "\"}"));
	}

	private ResultActions verify(String phone, String otp) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"" + phone + "\",\"otp\":\"" + otp + "\"}"));
	}

	private int otpRows() {
		return jdbc.queryForObject("select count(*) from otp_code", Integer.class);
	}

}

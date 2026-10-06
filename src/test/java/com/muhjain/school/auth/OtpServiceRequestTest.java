package com.muhjain.school.auth;

import java.time.Duration;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpServiceRequestTest extends AbstractIntegrationTest {

	private static final String KNOWN = "+919812340002";

	@Autowired
	private OtpService otpService;

	@Autowired
	private OtpHasher hasher;

	@Test
	void knownPhoneGetsAHashedCodeUnknownPhoneGetsNothing() {
		addUser(KNOWN, Role.OFFICE_ADMIN);

		OtpRequestResponse known = otpService.request("98123 40002", "10.0.0.1");
		OtpRequestResponse unknown = otpService.request("98123 49999", "10.0.0.1");

		assertThat(known).isEqualTo(unknown);
		assertThat(known.expiresInSeconds()).isEqualTo(300);
		assertThat(known.resendAfterSeconds()).isEqualTo(60);
		String code = lastCodeSentTo(KNOWN);
		assertThat(code).matches("\\d{6}");
		assertThat(jdbc.queryForObject("select count(*) from otp_code", Integer.class)).isEqualTo(1);
		String hash = jdbc.queryForObject("select code_hash from otp_code", String.class);
		assertThat(hash).isEqualTo(hasher.hash(KNOWN, code)).doesNotContain(code);
		assertThat(jdbc.queryForObject("select channel from otp_code", String.class)).isEqualTo("LOG");
	}

	@Test
	void waitIsSixtySecondsBetweenCodes() {
		addUser(KNOWN, Role.OFFICE_ADMIN);
		otpService.request(KNOWN, "10.0.0.1");
		clock.advance(Duration.ofSeconds(20));

		assertThatThrownBy(() -> otpService.request(KNOWN, "10.0.0.1"))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("OTP_TOO_MANY_REQUESTS");
				assertThat(ex.getRetryAfterSeconds()).isEqualTo(40);
			});

		clock.advance(Duration.ofSeconds(40));
		otpService.request(KNOWN, "10.0.0.1");
	}

	@Test
	void twentyCodesFromOneIpInOneHourIsTheLimit() {
		for (int i = 0; i < 20; i++) {
			String phone = "+9198123400" + String.format("%02d", i + 10);
			addUser(phone, Role.OFFICE_ADMIN);
			otpService.request(phone, "10.0.0.9");
		}
		addUser("+919812349999", Role.OFFICE_ADMIN);

		assertThatThrownBy(() -> otpService.request("+919812349999", "10.0.0.9"))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("OTP_TOO_MANY_REQUESTS"));
		otpService.request("+919812349999", "10.0.0.10");
	}

	@Test
	void badPhoneIsValidationError() {
		assertThatThrownBy(() -> otpService.request("12345", "10.0.0.1"))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("VALIDATION"));
	}

}

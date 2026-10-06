package com.muhjain.school.auth;

import java.time.Duration;
import java.time.ZoneOffset;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpServiceVerifyTest extends AbstractIntegrationTest {

	private static final String PHONE = "+919812340002";

	@Autowired
	private OtpService otpService;

	@Test
	void rightCodeGivesTokenSetsLastLoginAndWritesAudit() {
		AppUser user = addUser(PHONE, Role.OFFICE_ADMIN);
		otpService.request(PHONE, "10.0.0.1");

		LoginResponse login = otpService.verify("98123 40002", lastCodeSentTo(PHONE));

		assertThat(login.token()).isNotBlank();
		assertThat(login.expiresAt().getOffset()).isEqualTo(ZoneOffset.ofHoursMinutes(5, 30));
		assertThat(login.user().id()).isEqualTo(user.getId());
		assertThat(login.user().role()).isEqualTo("OFFICE_ADMIN");
		assertThat(login.user().permissions()).contains("STUDENTS_EDIT").doesNotContain("USERS_MANAGE");
		assertThat(login.user().route()).isNull();
		assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt()).isNotNull();
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'LOGIN' and changed_by = ?",
				Integer.class, user.getId()))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("select consumed_at is not null from otp_code", Boolean.class)).isTrue();
	}

	@Test
	void wrongTriesAreSavedAndTheFifthLocksTheCode() {
		addUser(PHONE, Role.OFFICE_ADMIN);
		otpService.request(PHONE, "10.0.0.1");
		String right = lastCodeSentTo(PHONE);
		String wrong = right.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 5; i++) {
			assertCode(() -> otpService.verify(PHONE, wrong), "OTP_INVALID");
		}
		assertThat(jdbc.queryForObject("select attempts from otp_code", Integer.class)).isEqualTo(5);
		assertCode(() -> otpService.verify(PHONE, right), "OTP_LOCKED");
	}

	@Test
	void expiredCodeIsInvalid() {
		addUser(PHONE, Role.OFFICE_ADMIN);
		otpService.request(PHONE, "10.0.0.1");
		clock.advance(Duration.ofMinutes(5));

		assertCode(() -> otpService.verify(PHONE, lastCodeSentTo(PHONE)), "OTP_INVALID");
	}

	@Test
	void turnedOffUserGetsInvalidEvenWithTheRightCode() {
		AppUser user = addUser(PHONE, Role.OFFICE_ADMIN);
		otpService.request(PHONE, "10.0.0.1");
		user.setActive(false);
		userRepository.saveAndFlush(user);

		assertCode(() -> otpService.verify(PHONE, lastCodeSentTo(PHONE)), "OTP_INVALID");
	}

	private static void assertCode(Runnable call, String code) {
		assertThatThrownBy(call::run)
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
	}

}

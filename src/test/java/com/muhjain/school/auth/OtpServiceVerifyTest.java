package com.muhjain.school.auth;

import java.time.Duration;
import java.time.ZoneOffset;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.muhjain.school.model.dto.response.LoginResponse;
import com.muhjain.school.service.auth.OtpService;
import com.muhjain.school.service.auth.RedisOtpStore;

class OtpServiceVerifyTest extends AbstractIntegrationTest {

	private static final String PHONE = "+919812340002";

	@Autowired
	private OtpService otpService;

	@Autowired
	private RedisOtpStore otpStore;

	@Test
	void rightCodeGivesTokenSetsLastLoginAndWritesAudit() {
		AppUser user = addUser(PHONE, Role.OFFICE_ADMIN);
		otpService.request(PHONE, "10.0.0.1");

		LoginResponse login = otpService.verify("98123 40002", lastCodeSentTo(PHONE), "10.0.0.1");

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
		// A right code cannot be used twice: it is gone from Redis.
		assertThat(otpStore.find(PHONE)).isEmpty();
	}

	@Test
	void wrongTriesAreSavedAndTheThirdLocksTheCode() {
		addUser(PHONE, Role.OFFICE_ADMIN);
		otpService.request(PHONE, "10.0.0.1");
		String right = lastCodeSentTo(PHONE);
		String wrong = right.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 3; i++) {
			assertCode(() -> otpService.verify(PHONE, wrong, "10.0.0.1"), "OTP_INVALID");
		}
		assertThat(otpStore.find(PHONE)).get().extracting(RedisOtpStore.ActiveCode::attempts).isEqualTo(3);
		assertCode(() -> otpService.verify(PHONE, right, "10.0.0.1"), "OTP_LOCKED");
	}

	@Test
	void expiredCodeIsInvalid() {
		addUser(PHONE, Role.OFFICE_ADMIN);
		otpService.request(PHONE, "10.0.0.1");
		clock.advance(Duration.ofMinutes(5));

		assertCode(() -> otpService.verify(PHONE, lastCodeSentTo(PHONE), "10.0.0.1"), "OTP_INVALID");
	}

	@Test
	void turnedOffUserGetsInvalidEvenWithTheRightCode() {
		AppUser user = addUser(PHONE, Role.OFFICE_ADMIN);
		otpService.request(PHONE, "10.0.0.1");
		user.setActive(false);
		userRepository.saveAndFlush(user);

		assertCode(() -> otpService.verify(PHONE, lastCodeSentTo(PHONE), "10.0.0.1"), "OTP_INVALID");
	}

	private static void assertCode(Runnable call, String code) {
		assertThatThrownBy(call::run)
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
	}

}

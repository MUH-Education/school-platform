package com.muhjain.school.user;

import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppUserRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private AppUserRepository users;

	@Test
	void savedUserGetsIdAndTimesFromTheAppClock() {
		Instant fixed = Instant.parse("2026-10-07T03:30:00Z");
		clock.setInstant(fixed);

		AppUser saved = users.saveAndFlush(new AppUser("+919812340002", Role.OFFICE_ADMIN));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCreatedAt()).isEqualTo(fixed);
		assertThat(saved.getUpdatedAt()).isEqualTo(fixed);
		assertThat(saved.isActive()).isTrue();
		assertThat(saved.getTokenVersion()).isZero();
		assertThat(users.findByPhoneAndActiveTrue("+919812340002")).isPresent();
	}

	@Test
	void samePhoneTwiceIsRejectedByTheDatabase() {
		users.saveAndFlush(new AppUser("+919812340002", Role.OFFICE_ADMIN));

		assertThatThrownBy(() -> users.saveAndFlush(new AppUser("+919812340002", Role.OWNER)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void turnedOffUserIsNotFoundAsActive() {
		AppUser user = new AppUser("+919812340003", Role.ADMISSIONS_DESK);
		user.setActive(false);
		users.saveAndFlush(user);

		assertThat(users.findByPhoneAndActiveTrue("+919812340003")).isEmpty();
		assertThat(users.findByPhone("+919812340003")).isPresent();
	}

}

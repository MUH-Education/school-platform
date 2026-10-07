package com.muhjain.school.auth;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtpHasherTest {

	private final OtpHasher hasher = hasher("first-secret-for-the-otp-hasher-test");

	@Test
	void hashIs64HexCharactersAndNotTheCode() {
		String hash = hasher.hash("+919812340002", "482913");

		assertThat(hash).hasSize(64).matches("[0-9a-f]{64}").doesNotContain("482913");
	}

	@Test
	void rightCodeMatchesWrongCodeDoesNot() {
		String hash = hasher.hash("+919812340002", "482913");

		assertThat(hasher.matches("+919812340002", "482913", hash)).isTrue();
		assertThat(hasher.matches("+919812340002", "482914", hash)).isFalse();
	}

	@Test
	void sameCodeForAnotherPhoneOrAnotherSecretGivesAnotherHash() {
		String hash = hasher.hash("+919812340002", "482913");

		assertThat(hasher.hash("+919812340003", "482913")).isNotEqualTo(hash);
		assertThat(hasher("other-secret-for-the-otp-hasher-test").hash("+919812340002", "482913")).isNotEqualTo(hash);
	}

	private static OtpHasher hasher(String secret) {
		return new OtpHasher(new OtpProperties(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60), 5, 20,
				List.of("log"), secret));
	}

}

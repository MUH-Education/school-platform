package com.muhjain.school.auth;

import java.time.Duration;
import java.util.List;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthPropertiesTest extends AbstractIntegrationTest {

	private static final String SECRET_32 = "x".repeat(32);

	@Autowired
	private JwtProperties jwtProperties;

	@Autowired
	private OtpProperties otpProperties;

	@Test
	void valuesFromApplicationYmlAreBound() {
		assertThat(jwtProperties.ttl()).isEqualTo(Duration.ofDays(30));
		assertThat(otpProperties.length()).isEqualTo(6);
		assertThat(otpProperties.ttl()).isEqualTo(Duration.ofMinutes(5));
		assertThat(otpProperties.maxAttempts()).isEqualTo(5);
		assertThat(otpProperties.resendAfter()).isEqualTo(Duration.ofSeconds(60));
		assertThat(otpProperties.maxPerHour()).isEqualTo(5);
		assertThat(otpProperties.maxPerIpPerHour()).isEqualTo(20);
		assertThat(otpProperties.channels()).containsExactly("LOG");
	}

	@Test
	void jwtSecretShorterThan32BytesStopsTheApp() {
		assertThatThrownBy(() -> new JwtProperties("x".repeat(31), Duration.ofDays(30)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least 32 bytes");
		assertThat(new JwtProperties(SECRET_32, Duration.ofDays(30)).secretBytes()).hasSize(32);
	}

	@Test
	void missingSecretsStopTheApp() {
		assertThatThrownBy(() -> new JwtProperties(null, Duration.ofDays(30))).hasMessageContaining("APP_JWT_SECRET");
		assertThatThrownBy(() -> otp(" ")).hasMessageContaining("APP_OTP_SECRET");
		assertThatThrownBy(() -> otp("short")).hasMessageContaining("at least 32 bytes");
	}

	@Test
	void channelsAreCleanedToCapitals() {
		assertThat(otp(SECRET_32).channels()).containsExactly("WHATSAPP", "SMS");
	}

	private static OtpProperties otp(String hashSecret) {
		return new OtpProperties(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60), 5, 20, List.of(" whatsapp", "sms"),
				hashSecret);
	}

}

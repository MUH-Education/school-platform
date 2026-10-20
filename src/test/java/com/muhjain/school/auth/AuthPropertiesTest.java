package com.muhjain.school.auth;

import java.time.Duration;
import java.util.List;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.muhjain.school.config.OtpProperties;
import com.muhjain.school.security.JwtProperties;
import com.muhjain.school.security.SessionProperties;

class AuthPropertiesTest extends AbstractIntegrationTest {

	private static final String SECRET_32 = "x".repeat(32);

	@Autowired
	private JwtProperties jwtProperties;

	@Autowired
	private SessionProperties sessionProperties;

	@Autowired
	private OtpProperties otpProperties;

	@Test
	void valuesFromApplicationYmlAreBound() {
		assertThat(jwtProperties.secretBytes()).isNotEmpty();
		assertThat(sessionProperties.idleTimeout()).isEqualTo(Duration.ofMinutes(15));
		assertThat(sessionProperties.maxLifetime()).isEqualTo(Duration.ofHours(12));
		assertThat(otpProperties.length()).isEqualTo(6);
		assertThat(otpProperties.ttl()).isEqualTo(Duration.ofMinutes(5));
		assertThat(otpProperties.maxAttempts()).isEqualTo(3);
		assertThat(otpProperties.resendAfter()).isEqualTo(Duration.ofSeconds(60));
		assertThat(otpProperties.maxPerHour()).isEqualTo(5);
		assertThat(otpProperties.maxPerIpPerHour()).isEqualTo(20);
		assertThat(otpProperties.channels()).containsExactly("LOG");
	}

	@Test
	void jwtSecretShorterThan32BytesStopsTheApp() {
		assertThatThrownBy(() -> new JwtProperties("x".repeat(31)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least 32 bytes");
		assertThat(new JwtProperties(SECRET_32).secretBytes()).hasSize(32);
	}

	@Test
	void missingSecretsStopTheApp() {
		assertThatThrownBy(() -> new JwtProperties(null)).hasMessageContaining("APP_JWT_SECRET");
		assertThatThrownBy(() -> otp(" ")).hasMessageContaining("APP_OTP_SECRET");
		assertThatThrownBy(() -> otp("short")).hasMessageContaining("at least 32 bytes");
		assertThatThrownBy(() -> new JwtProperties("${APP_JWT_SECRET}")).hasMessageContaining("not set");
		assertThatThrownBy(() -> session(" ")).hasMessageContaining("APP_SESSION_SECRET");
		assertThatThrownBy(() -> session("short")).hasMessageContaining("at least 32 bytes");
	}

	@Test
	void sessionMaxLifetimeMustBeAtLeastTheIdleTimeout() {
		assertThatThrownBy(() -> new SessionProperties(Duration.ofMinutes(15), Duration.ofMinutes(10), SECRET_32))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least app.session.idle-timeout");
	}

	@Test
	void channelsAreCleanedToCapitals() {
		assertThat(otp(SECRET_32).channels()).containsExactly("WHATSAPP", "SMS");
	}

	private static OtpProperties otp(String hashSecret) {
		return new OtpProperties(6, Duration.ofMinutes(5), 3, Duration.ofSeconds(60), 5, 20, List.of(" whatsapp", "sms"),
				hashSecret);
	}

	private static SessionProperties session(String hashSecret) {
		return new SessionProperties(Duration.ofMinutes(15), Duration.ofHours(12), hashSecret);
	}

}

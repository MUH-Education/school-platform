package com.muhjain.school.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.jwt.*}. Example: secret from APP_JWT_SECRET, ttl = 30d.
 * The app does not start if the secret is missing or shorter than 32 bytes.
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration ttl) {

	public static final int MIN_SECRET_BYTES = 32;

	public JwtProperties {
		// A missing environment variable stays as the text "${APP_...}".
		if (secret == null || secret.isBlank() || secret.startsWith("${")) {
			throw new IllegalStateException("app.jwt.secret is not set. Set the APP_JWT_SECRET environment variable.");
		}
		if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("app.jwt.secret is too short. APP_JWT_SECRET needs at least "
					+ MIN_SECRET_BYTES + " bytes.");
		}
		if (ttl == null || ttl.isNegative() || ttl.isZero()) {
			throw new IllegalStateException("app.jwt.ttl must be more than zero, for example 30d.");
		}
	}

	public byte[] secretBytes() {
		return secret.getBytes(StandardCharsets.UTF_8);
	}

}

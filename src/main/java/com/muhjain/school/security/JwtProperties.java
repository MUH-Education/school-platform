package com.muhjain.school.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * {@code app.jwt.*}. Example: secret from APP_JWT_SECRET.
 * The app does not start if the secret is missing or shorter than 32 bytes.
 * <p>
 * There is no {@code ttl} here: a token's lifetime is the session's own 12-hour hard cap
 * ({@code app.session.max-lifetime}), set by {@link SessionService} when it issues the token.
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret) {

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
	}

	public byte[] secretBytes() {
		return secret.getBytes(StandardCharsets.UTF_8);
	}

}

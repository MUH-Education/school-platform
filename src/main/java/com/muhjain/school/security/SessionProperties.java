package com.muhjain.school.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * {@code app.session.*}. Example: 15 minutes idle timeout, 12 hours hard cap.
 * <p>
 * A session slides: every request that uses it pushes the idle deadline another {@code idleTimeout}
 * into the future, so someone actively working never sees it expire. It can never run past
 * {@code maxLifetime} from login though, no matter how active they stay — a safety net against a
 * stolen token being kept alive forever.
 */
@ConfigurationProperties("app.session")
public record SessionProperties(Duration idleTimeout, Duration maxLifetime, String hashSecret) {

	public static final int MIN_SECRET_BYTES = 32;

	public SessionProperties {
		// A missing environment variable stays as the text "${APP_...}".
		if (hashSecret == null || hashSecret.isBlank() || hashSecret.startsWith("${")) {
			throw new IllegalStateException(
					"app.session.hash-secret is not set. Set the APP_SESSION_SECRET environment variable.");
		}
		if (hashSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"app.session.hash-secret is too short. APP_SESSION_SECRET needs at least " + MIN_SECRET_BYTES
							+ " bytes.");
		}
		if (idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()) {
			throw new IllegalStateException("app.session.idle-timeout must be more than zero, for example 15m.");
		}
		if (maxLifetime == null || maxLifetime.compareTo(idleTimeout) < 0) {
			throw new IllegalStateException("app.session.max-lifetime must be at least app.session.idle-timeout.");
		}
	}

}

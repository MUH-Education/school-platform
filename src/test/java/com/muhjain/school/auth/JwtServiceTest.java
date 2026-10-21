package com.muhjain.school.auth;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.muhjain.school.security.JwtService;

class JwtServiceTest extends AbstractIntegrationTest {

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Test
	void tokenHoldsUserIdAndSessionId() {
		Instant issuedAt = Instant.now(clock);
		Instant expiresAt = issuedAt.plus(Duration.ofHours(12));

		String token = jwtService.issue(2L, "a-session-id", issuedAt, expiresAt);
		Jwt jwt = jwtDecoder.decode(token);

		assertThat(jwt.getSubject()).isEqualTo("2");
		assertThat(jwt.getClaimAsString("sid")).isEqualTo("a-session-id");
		// A JWT's iat/exp are whole seconds (RFC 7519 NumericDate), so sub-second precision
		// from the wall clock does not round-trip.
		assertThat(jwt.getIssuedAt()).isEqualTo(issuedAt.truncatedTo(ChronoUnit.SECONDS));
		assertThat(jwt.getExpiresAt()).isEqualTo(expiresAt.truncatedTo(ChronoUnit.SECONDS));
		assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
	}

	@Test
	void changedTokenIsRejected() {
		Instant now = Instant.now(clock);
		String token = jwtService.issue(1L, "a-session-id", now, now.plus(Duration.ofHours(12)));
		String[] parts = token.split("\\.");
		String otherBody = java.util.Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString("{\"sub\":\"1\",\"sid\":\"someone-elses-session\"}".getBytes());

		assertThatThrownBy(() -> jwtDecoder.decode(parts[0] + "." + otherBody + "." + parts[2]))
			.isInstanceOf(JwtException.class);
	}

	@Test
	void tokenAfterItsOwnExpiryIsRejected() {
		Instant now = Instant.now(clock);
		String token = jwtService.issue(1L, "a-session-id", now, now.plus(Duration.ofHours(12)));
		clock.advance(Duration.ofHours(12).plusMinutes(1));

		assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
	}

}

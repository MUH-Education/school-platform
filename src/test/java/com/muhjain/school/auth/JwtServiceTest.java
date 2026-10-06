package com.muhjain.school.auth;

import java.time.Duration;
import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest extends AbstractIntegrationTest {

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Test
	void tokenHoldsUserIdRoleAndVersionForThirtyDays() {
		Instant now = Instant.parse("2026-10-07T03:45:00Z");
		clock.setInstant(now);
		AppUser user = addUser("+919812340002", Role.OFFICE_ADMIN);

		IssuedToken issued = jwtService.issue(user);
		Jwt jwt = jwtDecoder.decode(issued.token());

		assertThat(jwt.getSubject()).isEqualTo(String.valueOf(user.getId()));
		assertThat(jwt.getClaimAsString("role")).isEqualTo("OFFICE_ADMIN");
		assertThat(((Number) jwt.getClaim("ver")).intValue()).isZero();
		assertThat(jwt.getIssuedAt()).isEqualTo(now);
		assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofDays(30))).isEqualTo(issued.expiresAt());
		assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
	}

	@Test
	void changedTokenIsRejected() {
		String token = jwtService.issue(addUser("+919812340002", Role.OFFICE_ADMIN)).token();
		String[] parts = token.split("\\.");
		String otherBody = java.util.Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString("{\"sub\":\"1\",\"role\":\"OWNER\",\"ver\":0}".getBytes());

		assertThatThrownBy(() -> jwtDecoder.decode(parts[0] + "." + otherBody + "." + parts[2]))
			.isInstanceOf(JwtException.class);
	}

	@Test
	void tokenAfterThirtyDaysIsRejected() {
		String token = jwtService.issue(addUser("+919812340002", Role.OFFICE_ADMIN)).token();
		clock.advance(Duration.ofDays(30).plusMinutes(2));

		assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
	}

}

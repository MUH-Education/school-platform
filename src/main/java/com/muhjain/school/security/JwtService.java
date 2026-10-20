package com.muhjain.school.security;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Makes the login token. It holds only: sub (user id, for audit and logging), sid (the session
 * id), iat, exp. Who the user actually is comes from the session (Redis, or Postgres as a
 * fallback), never from a claim here — that is what lets a role change or a logout take effect
 * at once, without waiting for the token itself to expire.
 * <p>
 * {@code exp} is the session's 12-hour hard cap ({@code app.session.max-lifetime}), not the
 * 15-minute idle timeout: the idle timeout slides with use and a JWT cannot be edited after it is
 * made, so it is checked separately, on every request, against the session itself.
 */
@Service
public class JwtService {

	public static final String CLAIM_SESSION = "sid";

	private final JwtEncoder encoder;

	public JwtService(JwtEncoder encoder) {
		this.encoder = encoder;
	}

	public String issue(Long userId, String sessionId, Instant issuedAt, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(String.valueOf(userId))
			.claim(CLAIM_SESSION, sessionId)
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}

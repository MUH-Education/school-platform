package com.muhjain.school.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.dto.response.SessionCacheResult;
import com.muhjain.school.model.dto.response.SessionPrincipal;
import com.muhjain.school.model.enums.Permission;
import com.muhjain.school.service.auth.SessionService;
import com.muhjain.school.service.user.UserService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Runs on every request after the signature and the token's own (12-hour) expiry are checked.
 * <ol>
 * <li>Reads the session id ({@code sid}) and asks {@link SessionService} to resolve it — Redis
 * first (the normal path, no database call), Postgres only if Redis has forgotten it.</li>
 * <li>401 if the session is missing, idle-expired, past its hard cap, or revoked.</li>
 * <li>Authorities = {@code ROLE_<role>} plus each permission of the role, from the session's own
 * role, which was itself read from the database when the session was made or last reloaded.</li>
 * <li>Fills {@link RequestContext} for the rest of the request.</li>
 * </ol>
 * The authentication name is the user id, for example "2" — unchanged from before, so
 * {@link CurrentUser#id()} and every audit "changed by" column keep working as they did.
 */
@Component
public class UserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final UserService userService;

	private final SessionService sessionService;

	public UserJwtConverter(UserService userService, SessionService sessionService) {
		this.userService = userService;
		this.sessionService = sessionService;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		String sessionId = sessionId(jwt);
		SessionCacheResult cached = sessionService.resolveFromCache(sessionId);
		SessionPrincipal principal;
		if (cached.principal() != null) {
			principal = cached.principal();
		}
		else if (cached.entryExisted()) {
			// Idle- or hard-cap-expired: never fall back to Postgres for this (see SessionCacheResult).
			throw new InvalidBearerTokenException("The token is no longer valid");
		}
		else {
			principal = reloadFromDatabase(sessionId)
				.orElseThrow(() -> new InvalidBearerTokenException("The token is no longer valid"));
		}

		List<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
		for (Permission permission : principal.role().permissions()) {
			authorities.add(new SimpleGrantedAuthority(permission.name()));
		}
		RequestContext.set(new RequestContextData(principal.userId(), principal.role(), principal.staffId(), sessionId));
		return new JwtAuthenticationToken(jwt, authorities, String.valueOf(principal.userId()));
	}

	// Redis forgot this session (restart, eviction), but Postgres (and the token itself) may still
	// be good. One extra query, only on this rare path; the cache is refilled so the next request is
	// fast again.
	private Optional<SessionPrincipal> reloadFromDatabase(String sessionId) {
		return sessionService.resolveFromDatabase(sessionId)
			.flatMap(row -> userService.findById(row.userId())
				.filter(AppUser::isActive)
				.map(user -> {
					sessionService.repopulateCache(sessionId, user, row.createdAt());
					return new SessionPrincipal(user.getId(), user.getRole(), user.getStaffId());
				}));
	}

	private static String sessionId(Jwt jwt) {
		String sid = jwt.getClaimAsString(JwtService.CLAIM_SESSION);
		if (sid == null || sid.isBlank()) {
			throw new InvalidBearerTokenException("The token has no session id");
		}
		return sid;
	}

}

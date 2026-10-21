package com.muhjain.school.service.auth;

import com.muhjain.school.model.dto.response.IssuedToken;
import com.muhjain.school.model.dto.response.SessionCacheResult;
import com.muhjain.school.model.dto.response.SessionPrincipal;
import com.muhjain.school.model.dto.response.SessionRow;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.entity.UserSession;
import com.muhjain.school.model.enums.AuditAction;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.model.repository.UserSessionRepository;
import com.muhjain.school.security.JwtService;
import com.muhjain.school.security.SessionProperties;
import com.muhjain.school.service.audit.AuditService;
import com.muhjain.school.util.SessionIdHasher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * A login session: made once an OTP is checked and right, used on every later request, ended by
 * logout or by one of the things that must end every session of a user at once (role change,
 * phone change, turning a user off). See docs/04-login-otp-jwt.md.
 * <p>
 * Redis is the hot path: {@code session:<id>} holds who the session belongs to, and slides its
 * 15-minute idle deadline forward on every request that resolves it successfully (capped at the
 * 12-hour hard limit from login, {@code app.session.max-lifetime}). {@code user_session} in
 * Postgres is the fallback for the rare case Redis has forgotten a session that should still be
 * good, and the only place that can find every session of one user to revoke them all.
 * <p>
 * This service never loads {@link AppUser}: that is the caller's job (it already has the user at
 * login, and reloads it itself on the rare Postgres-fallback path), so this class cannot end up
 * needing the {@code user} feature's service, which would make two beans need each other.
 */
@Service
public class SessionService {

	private static final String SESSION_PREFIX = "session:";

	private static final String USER_SESSIONS_PREFIX = "user_sessions:";

	private final UserSessionRepository sessions;

	private final StringRedisTemplate redis;

	private final SessionIdHasher hasher;

	private final SessionProperties properties;

	private final JwtService jwtService;

	private final AuditService auditService;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	public SessionService(UserSessionRepository sessions, StringRedisTemplate redis, SessionIdHasher hasher,
			SessionProperties properties, JwtService jwtService, AuditService auditService, Clock clock) {
		this.sessions = sessions;
		this.redis = redis;
		this.hasher = hasher;
		this.properties = properties;
		this.jwtService = jwtService;
		this.auditService = auditService;
		this.clock = clock;
	}

	/** Step C of the login: a right OTP calls this once, with the user it just found. */
	@Transactional
	public IssuedToken create(AppUser user, String ip) {
		Instant now = now();
		String sessionId = newSessionId();
		UserSession row = new UserSession(user.getId(), hasher.hash(sessionId), ip);
		sessions.save(row);
		Instant absoluteExpiresAt = now.plus(properties.maxLifetime());
		cacheSet(sessionId, user, idleDeadline(now), absoluteExpiresAt);
		redis.opsForSet().add(USER_SESSIONS_PREFIX + user.getId(), sessionId);
		String token = jwtService.issue(user.getId(), sessionId, now, absoluteExpiresAt);
		return new IssuedToken(token, absoluteExpiresAt);
	}

	/**
	 * The hot path: Redis only, no Postgres. A hit also slides the idle deadline forward, so an
	 * actively used session never times out mid-use.
	 */
	public SessionCacheResult resolveFromCache(String sessionId) {
		String value = redis.opsForValue().get(SESSION_PREFIX + sessionId);
		if (value == null) {
			return SessionCacheResult.noEntry();
		}
		String[] parts = value.split("\\|", 5);
		Instant idleExpiresAt = Instant.ofEpochMilli(Long.parseLong(parts[3]));
		Instant absoluteExpiresAt = Instant.ofEpochMilli(Long.parseLong(parts[4]));
		Instant now = now();
		if (!now.isBefore(idleExpiresAt) || !now.isBefore(absoluteExpiresAt)) {
			// Known for certain to be expired (idle or hard cap) — never fall back to Postgres for this:
			// the database has no idea about idle time, and would happily say "still fine".
			return SessionCacheResult.expired();
		}
		Long userId = Long.valueOf(parts[0]);
		Role role = Role.valueOf(parts[1]);
		Long staffId = parts[2].isEmpty() ? null : Long.valueOf(parts[2]);
		rawCacheSet(sessionId, parts[0], parts[1], parts[2], idleDeadline(now, absoluteExpiresAt), absoluteExpiresAt);
		return SessionCacheResult.valid(new SessionPrincipal(userId, role, staffId));
	}

	/**
	 * The rare fallback: Redis had no entry at all (restarted, evicted, or a brand new instance) but
	 * the token itself (12-hour outer bound, checked by Spring before this ever runs) might still be
	 * within a session that is genuinely still alive. Does not know about idle time, only "revoked?"
	 * and "past the 12-hour cap?" — a Redis outage can give someone a little extra idle grace, never less.
	 */
	@Transactional(readOnly = true)
	public Optional<SessionRow> resolveFromDatabase(String sessionId) {
		Instant now = now();
		return sessions.findBySessionHash(hasher.hash(sessionId))
			.filter(row -> row.getRevokedAt() == null)
			.filter(row -> row.getCreatedAt().plus(properties.maxLifetime()).isAfter(now))
			.map(row -> new SessionRow(row.getUserId(), row.getCreatedAt()));
	}

	/** Puts a session back in Redis after the fallback path found it still good, with a fresh idle window. */
	public void repopulateCache(String sessionId, AppUser user, Instant createdAt) {
		Instant now = now();
		Instant absoluteExpiresAt = createdAt.plus(properties.maxLifetime());
		cacheSet(sessionId, user, idleDeadline(now, absoluteExpiresAt), absoluteExpiresAt);
		redis.opsForSet().add(USER_SESSIONS_PREFIX + user.getId(), sessionId);
	}

	/** Logout: only this one session stops working. Every other device of this user is unaffected. */
	@Transactional
	public void revoke(String sessionId) {
		redis.delete(SESSION_PREFIX + sessionId);
		String hash = hasher.hash(sessionId);
		sessions.findBySessionHash(hash).filter(row -> row.getRevokedAt() == null).ifPresent(row -> {
			row.revoke(now());
			sessions.save(row);
			redis.opsForSet().remove(USER_SESSIONS_PREFIX + row.getUserId(), sessionId);
			auditService.record("USER", row.getUserId(), AuditAction.UPDATED, "Logged out (this device)", null,
					row.getUserId());
		});
	}

	/**
	 * "Log out everywhere": phone change, role change, turning a user off. The caller already
	 * writes its own audit line for that change, so this does not write a second one.
	 */
	@Transactional
	public void revokeAll(Long userId) {
		sessions.revokeAllForUser(userId, now());
		String setKey = USER_SESSIONS_PREFIX + userId;
		Set<String> sessionIds = redis.opsForSet().members(setKey);
		if (sessionIds != null) {
			for (String sessionId : sessionIds) {
				redis.delete(SESSION_PREFIX + sessionId);
			}
		}
		redis.delete(setKey);
	}

	private void cacheSet(String sessionId, AppUser user, Instant idleExpiresAt, Instant absoluteExpiresAt) {
		rawCacheSet(sessionId, String.valueOf(user.getId()), user.getRole().name(),
				user.getStaffId() == null ? "" : String.valueOf(user.getStaffId()), idleExpiresAt, absoluteExpiresAt);
	}

	private void rawCacheSet(String sessionId, String userId, String role, String staffId, Instant idleExpiresAt,
			Instant absoluteExpiresAt) {
		String value = userId + "|" + role + "|" + staffId + "|" + idleExpiresAt.toEpochMilli() + "|"
				+ absoluteExpiresAt.toEpochMilli();
		redis.opsForValue().set(SESSION_PREFIX + sessionId, value, properties.maxLifetime());
	}

	private Instant idleDeadline(Instant now) {
		return idleDeadline(now, now.plus(properties.maxLifetime()));
	}

	// The idle window never pushes a session past its own hard cap.
	private Instant idleDeadline(Instant now, Instant absoluteExpiresAt) {
		Instant idle = now.plus(properties.idleTimeout());
		return idle.isBefore(absoluteExpiresAt) ? idle : absoluteExpiresAt;
	}

	private String newSessionId() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private Instant now() {
		return Instant.now(clock);
	}

}

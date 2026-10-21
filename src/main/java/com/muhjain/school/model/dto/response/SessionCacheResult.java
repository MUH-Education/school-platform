package com.muhjain.school.model.dto.response;

/**
 * The Redis cache's answer for one session id. Distinguishes "no entry at all" (safe to fall
 * back to Postgres, which may still know a genuinely live session) from "an entry existed but is
 * idle- or hard-cap-expired" (never fall back: Postgres cannot tell idle time at all and would
 * wrongly say "still fine"). Internal carrier between {@code SessionService} and
 * {@code UserJwtConverter}.
 */
public record SessionCacheResult(SessionPrincipal principal, boolean entryExisted) {

	public static SessionCacheResult noEntry() {
		return new SessionCacheResult(null, false);
	}

	public static SessionCacheResult expired() {
		return new SessionCacheResult(null, true);
	}

	public static SessionCacheResult valid(SessionPrincipal principal) {
		return new SessionCacheResult(principal, true);
	}

}

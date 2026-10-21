package com.muhjain.school.model.dto.response;

import java.time.Instant;

/**
 * The Postgres fallback's answer: this session belongs to this user, created at this time
 * (from which the 12-hour hard cap is worked out). No role or permissions here — the database
 * only knows "is this session still alive"; the caller reloads the user for the rest.
 * Internal carrier between {@code SessionService} and {@code UserJwtConverter}.
 */
public record SessionRow(Long userId, Instant createdAt) {

}

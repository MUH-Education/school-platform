package com.muhjain.school.auth;

import java.time.OffsetDateTime;

/**
 * The answer to a right OTP.
 * Example: {@code { "token": "eyJhbGciOiJIUzI1NiJ9...", "expiresAt": "2026-11-06T09:15:00+05:30", "user": {...} }}
 */
public record LoginResponse(String token, OffsetDateTime expiresAt, AuthUserResponse user) {

}

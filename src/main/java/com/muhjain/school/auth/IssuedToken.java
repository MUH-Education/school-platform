package com.muhjain.school.auth;

import java.time.Instant;

/** A new token and when it ends. Example: "eyJhbGciOiJIUzI1NiJ9...", 30 days from now. */
public record IssuedToken(String token, Instant expiresAt) {

}

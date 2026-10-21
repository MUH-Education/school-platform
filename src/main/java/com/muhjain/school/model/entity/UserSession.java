package com.muhjain.school.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One login session. The server trusts Redis for the hot path; this row is the fallback
 * (Redis restarted or emptied) and the only place "log out everywhere" can find every
 * session of one user. Example: user 2, hash "9f2c...", made 09:00, not revoked.
 */
@Entity
@Table(name = "user_session")
public class UserSession extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "session_hash", nullable = false, length = 64)
	private String sessionHash;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	protected UserSession() {
	}

	public UserSession(Long userId, String sessionHash, String ipAddress) {
		this.userId = userId;
		this.sessionHash = sessionHash;
		this.ipAddress = ipAddress;
	}

	/** Makes this one session stop working, without touching the user's other sessions. */
	public void revoke(Instant now) {
		this.revokedAt = now;
	}

	public Long getUserId() {
		return userId;
	}

	public String getSessionHash() {
		return sessionHash;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public String getIpAddress() {
		return ipAddress;
	}

}

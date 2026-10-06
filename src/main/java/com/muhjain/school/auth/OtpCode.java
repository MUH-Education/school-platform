package com.muhjain.school.auth;

import java.time.Instant;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One login code that was sent. Only the hash is stored, never the code.
 * Example: +919812340002, hash "9f2c...", LOG, expires 09:20, 0 attempts, not used.
 */
@Entity
@Table(name = "otp_code")
public class OtpCode extends BaseEntity {

	@Column(nullable = false, length = 13)
	private String phone;

	@Column(name = "code_hash", nullable = false, length = 64)
	private String codeHash;

	@Column(nullable = false, length = 10)
	private String channel;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "request_ip", length = 45)
	private String requestIp;

	protected OtpCode() {
	}

	public OtpCode(String phone, String codeHash, String channel, Instant expiresAt, String requestIp) {
		this.phone = phone;
		this.codeHash = codeHash;
		this.channel = channel;
		this.expiresAt = expiresAt;
		this.requestIp = requestIp;
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

	public void addWrongAttempt() {
		attempts++;
	}

	public void consume(Instant now) {
		this.consumedAt = now;
	}

	public String getPhone() {
		return phone;
	}

	public String getCodeHash() {
		return codeHash;
	}

	public String getChannel() {
		return channel;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public int getAttempts() {
		return attempts;
	}

	public Instant getConsumedAt() {
		return consumedAt;
	}

	public String getRequestIp() {
		return requestIp;
	}

}

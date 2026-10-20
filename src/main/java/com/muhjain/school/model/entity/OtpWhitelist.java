package com.muhjain.school.model.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A phone that always gets the same fixed OTP, and never a real WhatsApp/SMS message.
 * Example: +919999900001, "Dev test login".
 */
@Entity
@Table(name = "otp_whitelist")
public class OtpWhitelist {

	@Id
	@Column(length = 13)
	private String phone;

	@Column(length = 120)
	private String note;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected OtpWhitelist() {
	}

	public OtpWhitelist(String phone, String note, Instant createdAt) {
		this.phone = phone;
		this.note = note;
		this.createdAt = createdAt;
	}

	public String getPhone() {
		return phone;
	}

	public String getNote() {
		return note;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

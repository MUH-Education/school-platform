package com.muhjain.school.setting;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One school-wide number. Example: "transport.bus_fee_per_year" = "8800". */
@Entity
@Table(name = "app_setting")
public class AppSetting {

	@Id
	@Column(name = "key", length = 60)
	private String key;

	@Column(nullable = false, length = 300)
	private String value;

	@Column(name = "updated_by")
	private Long updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected AppSetting() {
	}

	public void change(String value, Long updatedBy, Instant updatedAt) {
		this.value = value;
		this.updatedBy = updatedBy;
		this.updatedAt = updatedAt;
	}

	public String getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}

package com.muhjain.school.user;

import java.time.Instant;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A person who can log in. Example: +919812340002, "Neelam", OFFICE_ADMIN, active, token version 0.
 */
@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

	@Column(nullable = false, length = 13)
	private String phone;

	@Column(length = 120)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Role role;

	// Only for ATTENDANT. Points at a staff row (Phase 2).
	@Column(name = "staff_id")
	private Long staffId;

	@Column(nullable = false)
	private boolean active = true;

	// +1 on logout, turn off, role change, phone change. Tokens with an older number stop working.
	@Column(name = "token_version", nullable = false)
	private int tokenVersion;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "created_by")
	private Long createdBy;

	protected AppUser() {
	}

	public AppUser(String phone, Role role) {
		this.phone = phone;
		this.role = role;
	}

	/** Makes every token of this user stop working. */
	public void bumpTokenVersion() {
		tokenVersion++;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

	public Long getStaffId() {
		return staffId;
	}

	public void setStaffId(Long staffId) {
		this.staffId = staffId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public int getTokenVersion() {
		return tokenVersion;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	public void setLastLoginAt(Instant lastLoginAt) {
		this.lastLoginAt = lastLoginAt;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(Long createdBy) {
		this.createdBy = createdBy;
	}

}

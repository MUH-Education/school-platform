package com.muhjain.school.student;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One phone number of a parent or relative. One phone = one row, even if it belongs to three children.
 * Example: +919812340208, "Ramesh". The links to the children are {@link StudentGuardian} rows.
 */
@Entity
@Table(name = "guardian")
public class Guardian extends BaseEntity {

	@Column(length = 120)
	private String name;

	// Always +91XXXXXXXXXX (PhoneNumbers.normalize).
	@Column(nullable = false, updatable = false, length = 13)
	private String phone;

	@Column(name = "preferred_language", nullable = false, length = 5)
	private String preferredLanguage = "hi";

	protected Guardian() {
	}

	public Guardian(String name, String phone) {
		this.name = name;
		this.phone = phone;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPhone() {
		return phone;
	}

	public String getPreferredLanguage() {
		return preferredLanguage;
	}

}

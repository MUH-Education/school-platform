package com.muhjain.school.staff;

import java.time.LocalDate;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A driver, attendant or helper. Example: "Jagdish", DRIVER, licence valid till 31 Mar 2029.
 * Only a DRIVER has a licence. For other people the two licence fields are null.
 */
@Entity
@Table(name = "staff")
public class Staff extends BaseEntity {

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 13)
	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(name = "staff_type", nullable = false, length = 20)
	private StaffType staffType;

	@Column(name = "licence_no", length = 30)
	private String licenceNo;

	// The last valid day of the licence.
	@Column(name = "licence_valid_till")
	private LocalDate licenceValidTill;

	@Column(nullable = false)
	private boolean active = true;

	protected Staff() {
	}

	public Staff(String name, String phone, StaffType staffType) {
		this.name = name;
		this.phone = phone;
		this.staffType = staffType;
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

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public StaffType getStaffType() {
		return staffType;
	}

	public void setStaffType(StaffType staffType) {
		this.staffType = staffType;
	}

	public String getLicenceNo() {
		return licenceNo;
	}

	public void setLicenceNo(String licenceNo) {
		this.licenceNo = licenceNo;
	}

	public LocalDate getLicenceValidTill() {
		return licenceValidTill;
	}

	public void setLicenceValidTill(LocalDate licenceValidTill) {
		this.licenceValidTill = licenceValidTill;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}

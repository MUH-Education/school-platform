package com.muhjain.school.staff;

import java.time.LocalDate;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One employee of the school: a driver, attendant, helper or teacher.
 * Example: "Jagdish", DRIVER, licence valid till 31 Mar 2029, joined 1 Apr 2024.
 * Only a DRIVER has a licence. For other people the two licence fields are null.
 * Everything below {@code active} is the shared employee file and may be null: the people added in
 * Phase 2 have none of it filled in. Teaching extras live in {@link TeacherProfile}, salary in
 * {@link StaffSalary}.
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

	// The shared employee file. All of it may be null.

	@Column(name = "joined_on")
	private LocalDate joinedOn;

	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private StaffGender gender;

	@Column(length = 300)
	private String address;

	// Whom to call if something happens. Stored as +91XXXXXXXXXX, like every phone.
	@Column(name = "emergency_phone", length = 13)
	private String emergencyPhone;

	@Enumerated(EnumType.STRING)
	@Column(name = "id_proof_type", length = 20)
	private IdProofType idProofType;

	// Only the last 4 digits, never the whole number (question C11).
	@Column(name = "id_proof_last4", length = 4)
	private String idProofLast4;

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

	public LocalDate getJoinedOn() {
		return joinedOn;
	}

	public void setJoinedOn(LocalDate joinedOn) {
		this.joinedOn = joinedOn;
	}

	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	public void setDateOfBirth(LocalDate dateOfBirth) {
		this.dateOfBirth = dateOfBirth;
	}

	public StaffGender getGender() {
		return gender;
	}

	public void setGender(StaffGender gender) {
		this.gender = gender;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getEmergencyPhone() {
		return emergencyPhone;
	}

	public void setEmergencyPhone(String emergencyPhone) {
		this.emergencyPhone = emergencyPhone;
	}

	public IdProofType getIdProofType() {
		return idProofType;
	}

	public void setIdProofType(IdProofType idProofType) {
		this.idProofType = idProofType;
	}

	public String getIdProofLast4() {
		return idProofLast4;
	}

	public void setIdProofLast4(String idProofLast4) {
		this.idProofLast4 = idProofLast4;
	}

}

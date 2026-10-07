package com.muhjain.school.student;

import java.time.LocalDate;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One child. Never deleted: when the child leaves, {@code status} is LEFT and {@code leftOn} is set.
 * Example: A-2026-118, Aryan, class 3, section B, village Jakhal.
 * The parents' phones are in {@link StudentGuardian}, the bus is in {@link TransportEnrolment}.
 */
@Entity
@Table(name = "student")
public class Student extends BaseEntity {

	// Made by the server and never changed (rule 15). Example: "A-2026-118".
	@Column(name = "admission_no", nullable = false, updatable = false, length = 20)
	private String admissionNo;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false)
	private LocalDate dob;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 1)
	private Gender gender;

	// "Nursery", "LKG", "UKG", "1" to "12". See ClassNames.
	@Column(name = "class_name", nullable = false, length = 10)
	private String className;

	@Column(length = 4)
	private String section;

	@Column(nullable = false, length = 80)
	private String village;

	@Column(length = 200)
	private String address;

	@Enumerated(EnumType.STRING)
	@Column(name = "father_occupation", nullable = false, length = 30)
	private FatherOccupation fatherOccupation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private StudentStatus status = StudentStatus.ACTIVE;

	@Column(name = "joined_on", nullable = false)
	private LocalDate joinedOn;

	@Column(name = "left_on")
	private LocalDate leftOn;

	// The key in photo storage. Null = no photo.
	@Column(name = "photo_key", length = 200)
	private String photoKey;

	@Column(name = "created_by")
	private Long createdBy;

	protected Student() {
	}

	public Student(String admissionNo, String name, LocalDate dob, Gender gender, String className, String section,
			String village, String address, FatherOccupation fatherOccupation, LocalDate joinedOn, Long createdBy) {
		this.admissionNo = admissionNo;
		this.name = name;
		this.dob = dob;
		this.gender = gender;
		this.className = className;
		this.section = section;
		this.village = village;
		this.address = address;
		this.fatherOccupation = fatherOccupation;
		this.joinedOn = joinedOn;
		this.createdBy = createdBy;
	}

	public String getAdmissionNo() {
		return admissionNo;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LocalDate getDob() {
		return dob;
	}

	public void setDob(LocalDate dob) {
		this.dob = dob;
	}

	public Gender getGender() {
		return gender;
	}

	public void setGender(Gender gender) {
		this.gender = gender;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getSection() {
		return section;
	}

	public void setSection(String section) {
		this.section = section;
	}

	public String getVillage() {
		return village;
	}

	public void setVillage(String village) {
		this.village = village;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public FatherOccupation getFatherOccupation() {
		return fatherOccupation;
	}

	public void setFatherOccupation(FatherOccupation fatherOccupation) {
		this.fatherOccupation = fatherOccupation;
	}

	public StudentStatus getStatus() {
		return status;
	}

	public void setStatus(StudentStatus status) {
		this.status = status;
	}

	public LocalDate getJoinedOn() {
		return joinedOn;
	}

	public void setJoinedOn(LocalDate joinedOn) {
		this.joinedOn = joinedOn;
	}

	public LocalDate getLeftOn() {
		return leftOn;
	}

	public void setLeftOn(LocalDate leftOn) {
		this.leftOn = leftOn;
	}

	public String getPhotoKey() {
		return photoKey;
	}

	public void setPhotoKey(String photoKey) {
		this.photoKey = photoKey;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

}

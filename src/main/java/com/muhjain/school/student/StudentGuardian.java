package com.muhjain.school.student;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * "This phone belongs to this child." Example: Aryan and Siya are brother and sister. Their mother's phone is one
 * {@link Guardian} row with two of these rows, one for each child.
 */
@Entity
@Table(name = "student_guardian")
public class StudentGuardian extends BaseEntity {

	@Column(name = "student_id", nullable = false, updatable = false)
	private Long studentId;

	@Column(name = "guardian_id", nullable = false, updatable = false)
	private Long guardianId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GuardianRelation relation;

	// "Send bus SMS to this number". Example: the grandfather wants no SMS for Siya but wants it for Aryan.
	@Column(name = "sms_enabled", nullable = false)
	private boolean smsEnabled = true;

	@Column(name = "is_primary", nullable = false)
	private boolean primary;

	protected StudentGuardian() {
	}

	public StudentGuardian(Long studentId, Long guardianId, GuardianRelation relation, boolean smsEnabled,
			boolean primary) {
		this.studentId = studentId;
		this.guardianId = guardianId;
		this.relation = relation;
		this.smsEnabled = smsEnabled;
		this.primary = primary;
	}

	public Long getStudentId() {
		return studentId;
	}

	public Long getGuardianId() {
		return guardianId;
	}

	public GuardianRelation getRelation() {
		return relation;
	}

	public void setRelation(GuardianRelation relation) {
		this.relation = relation;
	}

	public boolean isSmsEnabled() {
		return smsEnabled;
	}

	public void setSmsEnabled(boolean smsEnabled) {
		this.smsEnabled = smsEnabled;
	}

	public boolean isPrimary() {
		return primary;
	}

	public void setPrimary(boolean primary) {
		this.primary = primary;
	}

}

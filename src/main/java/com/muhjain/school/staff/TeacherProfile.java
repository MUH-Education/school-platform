package com.muhjain.school.staff;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The extra file of one teacher. The key is the {@code staff} id, so one teacher has at most one row.
 * Example: staff 31, "B.Ed, M.A. Hindi", "Hindi, Social Science", class teacher of "3".
 * A teacher with no class has {@code classTeacherOf} null.
 */
@Entity
@Table(name = "teacher_profile")
public class TeacherProfile {

	@Id
	@Column(name = "staff_id")
	private Long staffId;

	@Column(length = 120)
	private String qualification;

	// One text box, not a table. Example: "Hindi, Social Science".
	@Column(length = 200)
	private String subjects;

	@Column(name = "class_teacher_of", length = 20)
	private String classTeacherOf;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected TeacherProfile() {
	}

	public TeacherProfile(Long staffId, Instant updatedAt) {
		this.staffId = staffId;
		this.updatedAt = updatedAt;
	}

	/** Replaces the whole file, like the edit form shows it. A null clears that field. */
	public void change(String qualification, String subjects, String classTeacherOf, Instant updatedAt) {
		this.qualification = qualification;
		this.subjects = subjects;
		this.classTeacherOf = classTeacherOf;
		this.updatedAt = updatedAt;
	}

	public Long getStaffId() {
		return staffId;
	}

	public String getQualification() {
		return qualification;
	}

	public String getSubjects() {
		return subjects;
	}

	public String getClassTeacherOf() {
		return classTeacherOf;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}

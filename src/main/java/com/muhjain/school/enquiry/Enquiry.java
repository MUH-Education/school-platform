package com.muhjain.school.enquiry;

import java.time.LocalDate;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One parent asking about admission. Example: Ramesh Jain from Jakhal asks about Class 3, source WALK_IN, NEW.
 * Other features are ids, not objects.
 */
@Entity
@Table(name = "enquiry")
public class Enquiry extends BaseEntity {

	@Column(name = "parent_name", nullable = false, length = 120)
	private String parentName;

	@Column(nullable = false, length = 13)
	private String phone;

	@Column(length = 20)
	private String relation;

	@Column(nullable = false, length = 80)
	private String village;

	@Column(name = "child_name", length = 120)
	private String childName;

	@Column(name = "class_sought", nullable = false, length = 10)
	private String classSought;

	@Column(name = "child_age", length = 20)
	private String childAge;

	@Column(name = "current_school", length = 120)
	private String currentSchool;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EnquirySource source;

	@Column(name = "referred_by", length = 120)
	private String referredBy;

	@Column(name = "referred_by_guardian_id")
	private Long referredByGuardianId;

	@Enumerated(EnumType.STRING)
	@Column(name = "needs_bus", nullable = false, length = 10)
	private NeedsBus needsBus = NeedsBus.UNKNOWN;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private EnquiryStatus status = EnquiryStatus.NEW;

	@Column(name = "lost_reason", length = 200)
	private String lostReason;

	@Column(name = "next_follow_up_on")
	private LocalDate nextFollowUpOn;

	@Column(length = 1000)
	private String note;

	@Column(name = "session_name", nullable = false, length = 9)
	private String sessionName;

	@Column(name = "admitted_student_id")
	private Long admittedStudentId;

	@Column(name = "created_by")
	private Long createdBy;

	protected Enquiry() {
	}

	public Enquiry(Long createdBy) {
		this.createdBy = createdBy;
	}

	/** Copies the details (not the stage, the lost reason, the admission or who created it) from another object. */
	void copyDetailsFrom(Enquiry other) {
		this.parentName = other.parentName;
		this.phone = other.phone;
		this.relation = other.relation;
		this.village = other.village;
		this.childName = other.childName;
		this.classSought = other.classSought;
		this.childAge = other.childAge;
		this.currentSchool = other.currentSchool;
		this.source = other.source;
		this.referredBy = other.referredBy;
		this.referredByGuardianId = other.referredByGuardianId;
		this.needsBus = other.needsBus;
		this.nextFollowUpOn = other.nextFollowUpOn;
		this.note = other.note;
		this.sessionName = other.sessionName;
	}

	public String getParentName() {
		return parentName;
	}

	public void setParentName(String parentName) {
		this.parentName = parentName;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getRelation() {
		return relation;
	}

	public void setRelation(String relation) {
		this.relation = relation;
	}

	public String getVillage() {
		return village;
	}

	public void setVillage(String village) {
		this.village = village;
	}

	public String getChildName() {
		return childName;
	}

	public void setChildName(String childName) {
		this.childName = childName;
	}

	public String getClassSought() {
		return classSought;
	}

	public void setClassSought(String classSought) {
		this.classSought = classSought;
	}

	public String getChildAge() {
		return childAge;
	}

	public void setChildAge(String childAge) {
		this.childAge = childAge;
	}

	public String getCurrentSchool() {
		return currentSchool;
	}

	public void setCurrentSchool(String currentSchool) {
		this.currentSchool = currentSchool;
	}

	public EnquirySource getSource() {
		return source;
	}

	public void setSource(EnquirySource source) {
		this.source = source;
	}

	public String getReferredBy() {
		return referredBy;
	}

	public void setReferredBy(String referredBy) {
		this.referredBy = referredBy;
	}

	public Long getReferredByGuardianId() {
		return referredByGuardianId;
	}

	public void setReferredByGuardianId(Long referredByGuardianId) {
		this.referredByGuardianId = referredByGuardianId;
	}

	public NeedsBus getNeedsBus() {
		return needsBus;
	}

	public void setNeedsBus(NeedsBus needsBus) {
		this.needsBus = needsBus;
	}

	public EnquiryStatus getStatus() {
		return status;
	}

	public void setStatus(EnquiryStatus status) {
		this.status = status;
	}

	public String getLostReason() {
		return lostReason;
	}

	public void setLostReason(String lostReason) {
		this.lostReason = lostReason;
	}

	public LocalDate getNextFollowUpOn() {
		return nextFollowUpOn;
	}

	public void setNextFollowUpOn(LocalDate nextFollowUpOn) {
		this.nextFollowUpOn = nextFollowUpOn;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public String getSessionName() {
		return sessionName;
	}

	public void setSessionName(String sessionName) {
		this.sessionName = sessionName;
	}

	public Long getAdmittedStudentId() {
		return admittedStudentId;
	}

	public void setAdmittedStudentId(Long admittedStudentId) {
		this.admittedStudentId = admittedStudentId;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

}

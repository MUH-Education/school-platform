package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.*;
import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * One parent asking about admission. Example: Ramesh Jain from Jakhal asks about Class 3, source WALK_IN, NEW.
 * Other features are ids, not objects.
 */
@Entity
@Table(name = "enquiry")
public class Enquiry extends BaseEntity {

	@Column(name = "session_id", nullable = false)
	private Long sessionId;

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

	@Column(name = "needs_bus_from", length = 80)
	private String needsBusFrom;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private EnquiryStatus status = EnquiryStatus.NEW;

	// Set when the status becomes LOST, so "open the enquiry again" knows where to go back to.
	@Enumerated(EnumType.STRING)
	@Column(name = "status_before_lost", length = 12)
	private EnquiryStatus statusBeforeLost;

	@Enumerated(EnumType.STRING)
	@Column(name = "lost_reason", length = 30)
	private EnquiryLostReason lostReason;

	@Column(name = "lost_note", length = 300)
	private String lostNote;

	@Column(name = "next_follow_up_on")
	private LocalDate nextFollowUpOn;

	@Enumerated(EnumType.STRING)
	@Column(name = "next_follow_up_type", length = 10)
	private EnquiryNextType nextFollowUpType;

	@Column(length = 1000)
	private String note;

	@Column(name = "admitted_student_id")
	private Long admittedStudentId;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	protected Enquiry() {
	}

	public Enquiry(Long createdBy) {
		this.createdBy = createdBy;
	}

	/** Copies the details (not the stage, the lost reason, the admission or who created it) from another object. */
	public void copyDetailsFrom(Enquiry other) {
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
		this.needsBusFrom = other.needsBusFrom;
		this.nextFollowUpOn = other.nextFollowUpOn;
		this.nextFollowUpType = other.nextFollowUpType;
		this.note = other.note;
		this.sessionId = other.sessionId;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public void setSessionId(Long sessionId) {
		this.sessionId = sessionId;
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

	public String getNeedsBusFrom() {
		return needsBusFrom;
	}

	public void setNeedsBusFrom(String needsBusFrom) {
		this.needsBusFrom = needsBusFrom;
	}

	public EnquiryStatus getStatus() {
		return status;
	}

	public void setStatus(EnquiryStatus status) {
		this.status = status;
	}

	public EnquiryStatus getStatusBeforeLost() {
		return statusBeforeLost;
	}

	public void setStatusBeforeLost(EnquiryStatus statusBeforeLost) {
		this.statusBeforeLost = statusBeforeLost;
	}

	public EnquiryLostReason getLostReason() {
		return lostReason;
	}

	public void setLostReason(EnquiryLostReason lostReason) {
		this.lostReason = lostReason;
	}

	public String getLostNote() {
		return lostNote;
	}

	public void setLostNote(String lostNote) {
		this.lostNote = lostNote;
	}

	public LocalDate getNextFollowUpOn() {
		return nextFollowUpOn;
	}

	public void setNextFollowUpOn(LocalDate nextFollowUpOn) {
		this.nextFollowUpOn = nextFollowUpOn;
	}

	public EnquiryNextType getNextFollowUpType() {
		return nextFollowUpType;
	}

	public void setNextFollowUpType(EnquiryNextType nextFollowUpType) {
		this.nextFollowUpType = nextFollowUpType;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
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

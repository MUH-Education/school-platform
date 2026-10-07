package com.muhjain.school.enquiry;

import java.time.LocalDate;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One call or visit. Example: "Called, will visit on Sunday", next action 12 Oct. */
@Entity
@Table(name = "enquiry_follow_up")
public class EnquiryFollowUp extends BaseEntity {

	@Column(name = "enquiry_id", nullable = false)
	private Long enquiryId;

	@Column(nullable = false, length = 1000)
	private String note;

	@Column(name = "next_action_on")
	private LocalDate nextActionOn;

	@Column(name = "created_by")
	private Long createdBy;

	protected EnquiryFollowUp() {
	}

	public EnquiryFollowUp(Long enquiryId, String note, LocalDate nextActionOn, Long createdBy) {
		this.enquiryId = enquiryId;
		this.note = note;
		this.nextActionOn = nextActionOn;
		this.createdBy = createdBy;
	}

	public Long getEnquiryId() {
		return enquiryId;
	}

	public String getNote() {
		return note;
	}

	public LocalDate getNextActionOn() {
		return nextActionOn;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

}

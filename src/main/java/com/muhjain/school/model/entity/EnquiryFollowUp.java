package com.muhjain.school.model.entity;

import java.time.LocalDate;

import com.muhjain.school.model.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import com.muhjain.school.model.enums.EnquiryFollowType;
import com.muhjain.school.model.enums.EnquiryNextType;

/** One call or visit. Example: CALL on 7 Oct, "Called, will visit on Sunday", next VISIT on 12 Oct. */
@Entity
@Table(name = "enquiry_follow_up")
public class EnquiryFollowUp extends BaseEntity {

	@Column(name = "enquiry_id", nullable = false)
	private Long enquiryId;

	@Column(name = "happened_on", nullable = false)
	private LocalDate happenedOn;

	@Enumerated(EnumType.STRING)
	@Column(name = "follow_type", nullable = false, length = 10)
	private EnquiryFollowType followType;

	@Column(nullable = false, length = 1000)
	private String note;

	@Enumerated(EnumType.STRING)
	@Column(name = "next_type", length = 10)
	private EnquiryNextType nextType;

	@Column(name = "next_on")
	private LocalDate nextOn;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	protected EnquiryFollowUp() {
	}

	public EnquiryFollowUp(Long enquiryId, LocalDate happenedOn, EnquiryFollowType followType, String note,
			EnquiryNextType nextType, LocalDate nextOn, Long createdBy) {
		this.enquiryId = enquiryId;
		this.happenedOn = happenedOn;
		this.followType = followType;
		this.note = note;
		this.nextType = nextType;
		this.nextOn = nextOn;
		this.createdBy = createdBy;
	}

	public Long getEnquiryId() {
		return enquiryId;
	}

	public LocalDate getHappenedOn() {
		return happenedOn;
	}

	public EnquiryFollowType getFollowType() {
		return followType;
	}

	public String getNote() {
		return note;
	}

	public EnquiryNextType getNextType() {
		return nextType;
	}

	public LocalDate getNextOn() {
		return nextOn;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

}

package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.EnquiryStatus;
import jakarta.persistence.*;

import java.time.LocalDate;

/** One stage reached by an enquiry, with its date. For the stage line "New 29 Sep → Contacted 2 Oct → ...". */
@Entity
@Table(name = "enquiry_stage")
public class EnquiryStage extends BaseEntity {

	@Column(name = "enquiry_id", nullable = false)
	private Long enquiryId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private EnquiryStatus status;

	@Column(name = "reached_on", nullable = false)
	private LocalDate reachedOn;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	protected EnquiryStage() {
	}

	public EnquiryStage(Long enquiryId, EnquiryStatus status, LocalDate reachedOn, Long createdBy) {
		this.enquiryId = enquiryId;
		this.status = status;
		this.reachedOn = reachedOn;
		this.createdBy = createdBy;
	}

	public Long getEnquiryId() {
		return enquiryId;
	}

	public EnquiryStatus getStatus() {
		return status;
	}

	public LocalDate getReachedOn() {
		return reachedOn;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

}

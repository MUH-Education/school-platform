package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.FamilyCreditReason;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/**
 * Money kept for a family: {@code +} adds credit, {@code -} uses it. Never updated or deleted. The credit of
 * a family is the sum of its rows.
 * Example: Tanvi leaves having paid up to December. The family keeps it: +6600 PAID_AHEAD_KEPT. Used later for
 * her brother: -6600 USED_IN_RECEIPT.
 */
@Entity
@Immutable
@Table(name = "family_credit_entry")
public class FamilyCreditEntry extends BaseEntity {

	@Column(name = "guardian_id", nullable = false)
	private Long guardianId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FamilyCreditReason reason;

	@Column(name = "student_id")
	private Long studentId;

	@Column(name = "receipt_id")
	private Long receiptId;

	@Column(length = 200)
	private String note;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	protected FamilyCreditEntry() {
	}

	public FamilyCreditEntry(Long guardianId, BigDecimal amount, FamilyCreditReason reason, Long studentId,
			Long receiptId, String note, Long createdBy) {
		this.guardianId = guardianId;
		this.amount = amount;
		this.reason = reason;
		this.studentId = studentId;
		this.receiptId = receiptId;
		this.note = note;
		this.createdBy = createdBy;
	}

	public Long getGuardianId() {
		return guardianId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public FamilyCreditReason getReason() {
		return reason;
	}

	public Long getStudentId() {
		return studentId;
	}

	public Long getReceiptId() {
		return receiptId;
	}

	public String getNote() {
		return note;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

}

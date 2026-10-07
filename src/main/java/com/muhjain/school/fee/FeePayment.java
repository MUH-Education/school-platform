package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Money the office received. Never edited, never deleted: there are no setters on purpose.
 * A correction is a new row with a negative amount and a note.
 * Example: SCHOOL 7500.00 by UPI on 1 Apr, receipt "R-2026-0412".
 */
@Entity
@Table(name = "fee_payment")
public class FeePayment extends BaseEntity {

	@Column(name = "student_id", nullable = false)
	private Long studentId;

	@Column(name = "session_id", nullable = false)
	private Long sessionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "fee_head", nullable = false, length = 10)
	private FeeHead feeHead;

	@Column(nullable = false)
	private BigDecimal amount;

	@Column(name = "paid_on", nullable = false)
	private LocalDate paidOn;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private PaymentMode mode;

	@Column(name = "receipt_no", nullable = false, length = 20)
	private String receiptNo;

	@Column(length = 200)
	private String note;

	@Column(name = "recorded_by")
	private Long recordedBy;

	protected FeePayment() {
	}

	public FeePayment(Long studentId, Long sessionId, FeeHead feeHead, BigDecimal amount, LocalDate paidOn,
			PaymentMode mode, String receiptNo, String note, Long recordedBy) {
		this.studentId = studentId;
		this.sessionId = sessionId;
		this.feeHead = feeHead;
		this.amount = amount;
		this.paidOn = paidOn;
		this.mode = mode;
		this.receiptNo = receiptNo;
		this.note = note;
		this.recordedBy = recordedBy;
	}

	public Long getStudentId() {
		return studentId;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public FeeHead getFeeHead() {
		return feeHead;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public LocalDate getPaidOn() {
		return paidOn;
	}

	public PaymentMode getMode() {
		return mode;
	}

	public String getReceiptNo() {
		return receiptNo;
	}

	public String getNote() {
		return note;
	}

	public Long getRecordedBy() {
		return recordedBy;
	}

}

package com.muhjain.school.model.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.muhjain.school.model.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import com.muhjain.school.model.enums.FeeHead;

/**
 * One line of a receipt: one child, one month, one fee head. Never updated or deleted: a database trigger
 * stops it. A cancel or a correction adds a new, negative line with {@code reversalOfId} pointing at the
 * line it reverses.
 * Example: receipt R-2026-1189, Aryan, September 2026, SCHOOL, 2500.00.
 */
@Entity
@Immutable
@Table(name = "fee_payment")
public class FeePayment extends BaseEntity {

	@Column(name = "receipt_id", nullable = false)
	private Long receiptId;

	@Column(name = "student_id", nullable = false)
	private Long studentId;

	@Column(name = "session_id", nullable = false)
	private Long sessionId;

	@Column(nullable = false)
	private LocalDate month;

	@Enumerated(EnumType.STRING)
	@Column(name = "fee_head", nullable = false, length = 10)
	private FeeHead feeHead;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "reversal_of_id")
	private Long reversalOfId;

	protected FeePayment() {
	}

	public FeePayment(Long receiptId, Long studentId, Long sessionId, LocalDate month, FeeHead feeHead,
			BigDecimal amount, Long reversalOfId) {
		this.receiptId = receiptId;
		this.studentId = studentId;
		this.sessionId = sessionId;
		this.month = month;
		this.feeHead = feeHead;
		this.amount = amount;
		this.reversalOfId = reversalOfId;
	}

	public Long getReceiptId() {
		return receiptId;
	}

	public Long getStudentId() {
		return studentId;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public LocalDate getMonth() {
		return month;
	}

	public FeeHead getFeeHead() {
		return feeHead;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Long getReversalOfId() {
		return reversalOfId;
	}

}

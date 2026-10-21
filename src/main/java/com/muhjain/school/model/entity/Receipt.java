package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.*;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One payment by a family, or one refund to a family. The lines ({@link FeePayment}) say which child and
 * month. A cancelled receipt stays, with status CANCELLED and a reason; nothing is deleted.
 * Example: R-2026-1189, PAYMENT, family of Punia, 46,000 by cash.
 */
@Entity
@Table(name = "receipt")
public class Receipt extends BaseEntity {

	@Column(name = "receipt_no", nullable = false, length = 20)
	private String receiptNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ReceiptKind kind = ReceiptKind.PAYMENT;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ReceiptSource source = ReceiptSource.COLLECT;

	@Column(name = "session_id", nullable = false)
	private Long sessionId;

	@Column(name = "family_guardian_id", nullable = false)
	private Long familyGuardianId;

	@Column(name = "paid_on", nullable = false)
	private LocalDate paidOn;

	@Column(name = "paid_at", nullable = false)
	private Instant paidAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private PaymentMode mode;

	@Column(length = 60)
	private String reference;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "credit_used", nullable = false, precision = 12, scale = 2)
	private final BigDecimal creditUsed = BigDecimal.ZERO;

	@Column(name = "late_fee_normal", nullable = false, precision = 12, scale = 2)
	private BigDecimal lateFeeNormal = BigDecimal.ZERO;

	@Column(name = "late_fee_final", nullable = false, precision = 12, scale = 2)
	private BigDecimal lateFeeFinal = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(name = "late_fee_reason", length = 20)
	private LateFeeReason lateFeeReason;

	@Column(name = "late_fee_note", length = 200)
	private String lateFeeNote;

	@Column(name = "late_fee_changed_by")
	private Long lateFeeChangedBy;

	@Column(name = "client_request_id", length = 64)
	private String clientRequestId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ReceiptStatus status = ReceiptStatus.ACTIVE;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "cancelled_by")
	private Long cancelledBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "cancel_reason", length = 20)
	private CancelReason cancelReason;

	@Column(name = "cancel_note", length = 300)
	private String cancelNote;

	@Column(name = "taken_by", nullable = false)
	private Long takenBy;

	protected Receipt() {
	}

	public Receipt(String receiptNo, ReceiptKind kind, ReceiptSource source, Long sessionId, Long familyGuardianId,
			LocalDate paidOn, Instant paidAt, PaymentMode mode, String reference, BigDecimal amount,
			String clientRequestId, Long takenBy) {
		this.receiptNo = receiptNo;
		this.kind = kind;
		this.source = source;
		this.sessionId = sessionId;
		this.familyGuardianId = familyGuardianId;
		this.paidOn = paidOn;
		this.paidAt = paidAt;
		this.mode = mode;
		this.reference = reference;
		this.amount = amount;
		this.clientRequestId = clientRequestId;
		this.takenBy = takenBy;
	}

	public String getReceiptNo() {
		return receiptNo;
	}

	public ReceiptKind getKind() {
		return kind;
	}

	public ReceiptSource getSource() {
		return source;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public Long getFamilyGuardianId() {
		return familyGuardianId;
	}

	public LocalDate getPaidOn() {
		return paidOn;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public PaymentMode getMode() {
		return mode;
	}

	public String getReference() {
		return reference;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BigDecimal getCreditUsed() {
		return creditUsed;
	}

	public BigDecimal getLateFeeNormal() {
		return lateFeeNormal;
	}

	public void setLateFeeNormal(BigDecimal lateFeeNormal) {
		this.lateFeeNormal = lateFeeNormal;
	}

	public BigDecimal getLateFeeFinal() {
		return lateFeeFinal;
	}

	public void setLateFeeFinal(BigDecimal lateFeeFinal) {
		this.lateFeeFinal = lateFeeFinal;
	}

	public LateFeeReason getLateFeeReason() {
		return lateFeeReason;
	}

	public void setLateFeeReason(LateFeeReason lateFeeReason) {
		this.lateFeeReason = lateFeeReason;
	}

	public String getLateFeeNote() {
		return lateFeeNote;
	}

	public void setLateFeeNote(String lateFeeNote) {
		this.lateFeeNote = lateFeeNote;
	}

	public Long getLateFeeChangedBy() {
		return lateFeeChangedBy;
	}

	public void setLateFeeChangedBy(Long lateFeeChangedBy) {
		this.lateFeeChangedBy = lateFeeChangedBy;
	}

	public String getClientRequestId() {
		return clientRequestId;
	}

	public ReceiptStatus getStatus() {
		return status;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public Long getCancelledBy() {
		return cancelledBy;
	}

	public CancelReason getCancelReason() {
		return cancelReason;
	}

	public String getCancelNote() {
		return cancelNote;
	}

	public Long getTakenBy() {
		return takenBy;
	}

	/** Rule: never undone. Cancelling needs a reason and a note. */
	public void cancel(CancelReason reason, String note, Long cancelledBy, Instant now) {
		this.status = ReceiptStatus.CANCELLED;
		this.cancelledAt = now;
		this.cancelledBy = cancelledBy;
		this.cancelReason = reason;
		this.cancelNote = note;
	}

}

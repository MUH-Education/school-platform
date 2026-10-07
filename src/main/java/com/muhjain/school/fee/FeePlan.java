package com.muhjain.school.fee;

import java.math.BigDecimal;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** What one child must pay in one session. Example: school 30000, bus 8800, discount 0, QUARTERLY. */
@Entity
@Table(name = "fee_plan")
public class FeePlan extends BaseEntity {

	@Column(name = "student_id", nullable = false)
	private Long studentId;

	@Column(name = "session_id", nullable = false)
	private Long sessionId;

	@Column(name = "school_fee", nullable = false)
	private BigDecimal schoolFee;

	@Column(name = "bus_fee", nullable = false)
	private BigDecimal busFee;

	@Column(nullable = false)
	private BigDecimal discount;

	@Enumerated(EnumType.STRING)
	@Column(name = "discount_reason", nullable = false, length = 20)
	private DiscountReason discountReason;

	@Enumerated(EnumType.STRING)
	@Column(name = "pay_frequency", nullable = false, length = 12)
	private PayFrequency payFrequency;

	@Column(name = "created_by")
	private Long createdBy;

	protected FeePlan() {
	}

	public FeePlan(Long studentId, Long sessionId, Long createdBy) {
		this.studentId = studentId;
		this.sessionId = sessionId;
		this.createdBy = createdBy;
		this.schoolFee = BigDecimal.ZERO;
		this.busFee = BigDecimal.ZERO;
		this.discount = BigDecimal.ZERO;
		this.discountReason = DiscountReason.NONE;
		this.payFrequency = PayFrequency.QUARTERLY;
	}

	public Long getStudentId() {
		return studentId;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public BigDecimal getSchoolFee() {
		return schoolFee;
	}

	public void setSchoolFee(BigDecimal schoolFee) {
		this.schoolFee = schoolFee;
	}

	public BigDecimal getBusFee() {
		return busFee;
	}

	public void setBusFee(BigDecimal busFee) {
		this.busFee = busFee;
	}

	public BigDecimal getDiscount() {
		return discount;
	}

	public void setDiscount(BigDecimal discount) {
		this.discount = discount;
	}

	public DiscountReason getDiscountReason() {
		return discountReason;
	}

	public void setDiscountReason(DiscountReason discountReason) {
		this.discountReason = discountReason;
	}

	public PayFrequency getPayFrequency() {
		return payFrequency;
	}

	public void setPayFrequency(PayFrequency payFrequency) {
		this.payFrequency = payFrequency;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

}

package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.DiscountReason;
import com.muhjain.school.model.enums.PayFrequency;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What one child pays, from a month on. The plan for month M is the row with the biggest {@code fromMonth}
 * that is {@code <= M}. The bus fee is not here: it is on {@code transport_enrolment}.
 * Example: Ishaan, 2500 a month from April. From January, 250 off because his brother joins: row 1 (from
 * April, 2500, no discount), row 2 (from January, 2500, discount 250, SIBLING).
 */
@Entity
@Table(name = "fee_plan")
public class FeePlan extends BaseEntity {

	@Column(name = "student_id", nullable = false)
	private Long studentId;

	@Column(name = "session_id", nullable = false)
	private Long sessionId;

	@Column(name = "from_month", nullable = false)
	private LocalDate fromMonth;

	@Column(name = "school_fee_per_month", nullable = false, precision = 12, scale = 2)
	private BigDecimal schoolFeePerMonth;

	@Column(name = "follows_class_fee", nullable = false)
	private boolean followsClassFee = true;

	@Column(name = "discount_per_month", nullable = false, precision = 12, scale = 2)
	private BigDecimal discountPerMonth = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(name = "discount_reason", nullable = false, length = 20)
	private DiscountReason discountReason = DiscountReason.NONE;

	@Enumerated(EnumType.STRING)
	@Column(name = "pay_frequency", nullable = false, length = 12)
	private PayFrequency payFrequency = PayFrequency.MONTHLY;

	@Column(length = 200)
	private String note;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	protected FeePlan() {
	}

	public FeePlan(Long studentId, Long sessionId, LocalDate fromMonth, BigDecimal schoolFeePerMonth,
			boolean followsClassFee, Long createdBy) {
		this.studentId = studentId;
		this.sessionId = sessionId;
		this.fromMonth = fromMonth;
		this.schoolFeePerMonth = schoolFeePerMonth;
		this.followsClassFee = followsClassFee;
		this.createdBy = createdBy;
	}

	public Long getStudentId() {
		return studentId;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public LocalDate getFromMonth() {
		return fromMonth;
	}

	public BigDecimal getSchoolFeePerMonth() {
		return schoolFeePerMonth;
	}

	public void setSchoolFeePerMonth(BigDecimal schoolFeePerMonth) {
		this.schoolFeePerMonth = schoolFeePerMonth;
	}

	public boolean isFollowsClassFee() {
		return followsClassFee;
	}

	public void setFollowsClassFee(boolean followsClassFee) {
		this.followsClassFee = followsClassFee;
	}

	public BigDecimal getDiscountPerMonth() {
		return discountPerMonth;
	}

	public void setDiscountPerMonth(BigDecimal discountPerMonth) {
		this.discountPerMonth = discountPerMonth;
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

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

}

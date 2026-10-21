package com.muhjain.school.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What one child must pay in one month. Made by the server from {@code fee_plan}, {@code transport_enrolment}
 * and {@code session_fee_setting}, for every month the child is in school. Late fees are not here: they are
 * counted when the family pays.
 * Example: Aryan, September 2026: school 2500.00, discount 0, bus 800.00.
 */
@Entity
@Table(name = "fee_due")
public class FeeDue extends BaseEntity {

	@Column(name = "student_id", nullable = false)
	private Long studentId;

	@Column(name = "session_id", nullable = false)
	private Long sessionId;

	@Column(nullable = false)
	private LocalDate month;

	@Column(name = "school_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal schoolAmount;

	@Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal discountAmount = BigDecimal.ZERO;

	@Column(name = "bus_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal busAmount = BigDecimal.ZERO;

	protected FeeDue() {
	}

	public FeeDue(Long studentId, Long sessionId, LocalDate month, BigDecimal schoolAmount,
			BigDecimal discountAmount, BigDecimal busAmount) {
		this.studentId = studentId;
		this.sessionId = sessionId;
		this.month = month;
		this.schoolAmount = schoolAmount;
		this.discountAmount = discountAmount;
		this.busAmount = busAmount;
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

	public BigDecimal getSchoolAmount() {
		return schoolAmount;
	}

	public void setSchoolAmount(BigDecimal schoolAmount) {
		this.schoolAmount = schoolAmount;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public void setDiscountAmount(BigDecimal discountAmount) {
		this.discountAmount = discountAmount;
	}

	public BigDecimal getBusAmount() {
		return busAmount;
	}

	public void setBusAmount(BigDecimal busAmount) {
		this.busAmount = busAmount;
	}

	/** School (after discount) plus bus. */
	public BigDecimal getTotal() {
		return schoolAmount.subtract(discountAmount).add(busAmount);
	}

}

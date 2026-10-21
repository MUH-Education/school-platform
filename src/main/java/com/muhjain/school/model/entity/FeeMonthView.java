package com.muhjain.school.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the read-only {@code v_fee_month} view: a {@code fee_due} row with what is paid for it and its
 * due/grace dates, already worked out by the database from {@code session_fee_setting}. This is the single
 * source of truth for "how much is owed, and since when" — the status (ON_TIME / DELAYED / DEFAULTED) on top
 * of it is worked out in Java by {@code FeeStatusCalculator}, from {@code balance}, {@code dueDate} and
 * {@code graceDate}.
 */
@Entity
@Immutable
@Table(name = "v_fee_month")
public class FeeMonthView {

	@Id
	@Column(name = "fee_due_id")
	private Long feeDueId;

	@Column(name = "student_id")
	private Long studentId;

	@Column(name = "session_id")
	private Long sessionId;

	private LocalDate month;

	@Column(name = "school_amount")
	private BigDecimal schoolAmount;

	@Column(name = "discount_amount")
	private BigDecimal discountAmount;

	@Column(name = "bus_amount")
	private BigDecimal busAmount;

	private BigDecimal total;

	@Column(name = "paid_school")
	private BigDecimal paidSchool;

	@Column(name = "paid_bus")
	private BigDecimal paidBus;

	@Column(name = "paid_late")
	private BigDecimal paidLate;

	private BigDecimal balance;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@Column(name = "grace_date")
	private LocalDate graceDate;

	@Column(name = "last_receipt_id")
	private Long lastReceiptId;

	protected FeeMonthView() {
	}

	public Long getFeeDueId() {
		return feeDueId;
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

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public BigDecimal getBusAmount() {
		return busAmount;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public BigDecimal getPaidSchool() {
		return paidSchool;
	}

	public BigDecimal getPaidBus() {
		return paidBus;
	}

	public BigDecimal getPaidLate() {
		return paidLate;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public LocalDate getGraceDate() {
		return graceDate;
	}

	public Long getLastReceiptId() {
		return lastReceiptId;
	}

}

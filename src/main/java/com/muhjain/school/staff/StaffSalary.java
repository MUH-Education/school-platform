package com.muhjain.school.staff;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What one employee is paid in a month. Example: staff 31, ₹18,500.00, last changed by user 1.
 * This is a table of its own, not a column on {@code staff}, so that the staff list can never show it
 * (decision B24). Only the salary of today is kept; raises are not a history.
 */
@Entity
@Table(name = "staff_salary")
public class StaffSalary {

	@Id
	@Column(name = "staff_id")
	private Long staffId;

	@Column(name = "monthly_salary", nullable = false, precision = 12, scale = 2)
	private BigDecimal monthlySalary;

	@Column(name = "updated_by")
	private Long updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected StaffSalary() {
	}

	public StaffSalary(Long staffId, BigDecimal monthlySalary, Long updatedBy, Instant updatedAt) {
		this.staffId = staffId;
		change(monthlySalary, updatedBy, updatedAt);
	}

	public void change(BigDecimal monthlySalary, Long updatedBy, Instant updatedAt) {
		this.monthlySalary = monthlySalary;
		this.updatedBy = updatedBy;
		this.updatedAt = updatedAt;
	}

	public Long getStaffId() {
		return staffId;
	}

	public BigDecimal getMonthlySalary() {
		return monthlySalary;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}

package com.muhjain.school.staff;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What one employee is paid in a month. Only for a user with STAFF_SALARY_VIEW.
 * Example: {@code { "staffId": 31, "monthlySalary": 18500.00, "updatedBy": 1,
 * "updatedAt": "2026-10-09T05:10:00Z" }}
 */
public record SalaryResponse(Long staffId, BigDecimal monthlySalary, Long updatedBy, Instant updatedAt) {

	static SalaryResponse of(StaffSalary salary) {
		return new SalaryResponse(salary.getStaffId(), salary.getMonthlySalary(), salary.getUpdatedBy(),
				salary.getUpdatedAt());
	}

}

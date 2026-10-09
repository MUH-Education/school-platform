package com.muhjain.school.staff;

import java.time.Clock;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditChanges;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What an employee is paid. Kept away from {@link StaffService} so that no staff list can ever carry a salary
 * by accident (decision B24). Only a user with STAFF_SALARY_VIEW or STAFF_SALARY_EDIT reaches this.
 */
@Service
public class StaffSalaryService {

	private final StaffSalaryRepository salaries;

	private final StaffService staffService;

	private final AuditService auditService;

	private final Clock clock;

	public StaffSalaryService(StaffSalaryRepository salaries, StaffService staffService, AuditService auditService,
			Clock clock) {
		this.salaries = salaries;
		this.staffService = staffService;
		this.auditService = auditService;
		this.clock = clock;
	}

	/**
	 * @throws ApiException 404 NOT_FOUND if there is no such person, or no salary saved for them yet
	 */
	@Transactional(readOnly = true)
	public SalaryResponse get(Long staffId) {
		staffService.nameOf(staffId);
		return salaries.findById(staffId)
			.map(SalaryResponse::of)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
					"No salary is saved for this person."));
	}

	/**
	 * Sets the salary of today. There is one row per person: setting it again replaces the number.
	 *
	 * @throws ApiException 404 NOT_FOUND if there is no such person
	 */
	@Transactional
	public SalaryResponse set(Long staffId, SetSalaryRequest request, Long changedBy) {
		String name = staffService.nameOf(staffId);
		StaffSalary salary = salaries.findById(staffId).orElse(null);
		AuditChanges changes = new AuditChanges().field("Salary", "monthlySalary",
				(salary == null) ? null : salary.getMonthlySalary(), request.monthlySalary());
		if (salary == null) {
			salary = new StaffSalary(staffId, request.monthlySalary(), changedBy, clock.instant());
		}
		else {
			salary.change(request.monthlySalary(), changedBy, clock.instant());
		}
		salary = salaries.saveAndFlush(salary);
		if (!changes.isEmpty()) {
			auditService.record(StaffService.ENTITY, staffId, AuditAction.UPDATED,
					name + ": " + changes.summary(), changes.details());
		}
		return SalaryResponse.of(salary);
	}

}

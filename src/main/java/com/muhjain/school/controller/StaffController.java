package com.muhjain.school.controller;

import com.muhjain.school.model.dto.request.CreateStaffRequest;
import com.muhjain.school.model.dto.request.SetSalaryRequest;
import com.muhjain.school.model.dto.request.UpdateStaffRequest;
import com.muhjain.school.model.dto.response.SalaryResponse;
import com.muhjain.school.model.dto.response.StaffResponse;
import com.muhjain.school.model.enums.StaffType;
import com.muhjain.school.security.CurrentUser;
import com.muhjain.school.service.staff.StaffSalaryService;
import com.muhjain.school.service.staff.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * The staff list of the Vehicles and staff screen, and the Employees screen. Reading needs VEHICLES_VIEW,
 * changing needs VEHICLES_EDIT. People are never deleted: DELETE turns one off.
 * The salary is separate: it needs STAFF_SALARY_VIEW or STAFF_SALARY_EDIT, which only the owner has.
 */
@Tag(name = "Staff")
@RestController
@RequestMapping("/api/v1/staff")
public class StaffController {

	private final StaffService staffService;

	private final StaffSalaryService salaryService;

	private final CurrentUser currentUser;

	public StaffController(StaffService staffService, StaffSalaryService salaryService, CurrentUser currentUser) {
		this.staffService = staffService;
		this.salaryService = salaryService;
		this.currentUser = currentUser;
	}

	/** Example: {@code GET /api/v1/staff?type=DRIVER&type=ATTENDANT&type=HELPER} leaves teachers out. */
	@Operation(summary = "List staff, all types or only some")
	@GetMapping
	@PreAuthorize("hasAuthority('VEHICLES_VIEW')")
	public List<StaffResponse> list(@RequestParam(name = "type", required = false) Set<StaffType> types) {
		return staffService.list(types);
	}

	@Operation(summary = "One staff member")
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('VEHICLES_VIEW')")
	public StaffResponse get(@PathVariable Long id) {
		return staffService.get(id);
	}

	@Operation(summary = "Add a staff member")
	@PostMapping
	@PreAuthorize("hasAuthority('VEHICLES_EDIT')")
	@ResponseStatus(HttpStatus.CREATED)
	public StaffResponse create(@Valid @RequestBody CreateStaffRequest request) {
		return staffService.create(request);
	}

	@Operation(summary = "Change a staff member")
	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('VEHICLES_EDIT')")
	public StaffResponse update(@PathVariable Long id, @Valid @RequestBody UpdateStaffRequest request) {
		return staffService.update(id, request);
	}

	@Operation(summary = "Turn a staff member off")
	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('VEHICLES_EDIT')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void turnOff(@PathVariable Long id) {
		staffService.turnOff(id);
	}

	@Operation(summary = "The salary of one staff member")
	@GetMapping("/{id}/salary")
	@PreAuthorize("hasAuthority('STAFF_SALARY_VIEW')")
	public SalaryResponse salary(@PathVariable Long id) {
		return salaryService.get(id);
	}

	@Operation(summary = "Set the salary of one staff member")
	@PutMapping("/{id}/salary")
	@PreAuthorize("hasAuthority('STAFF_SALARY_EDIT')")
	public SalaryResponse setSalary(@PathVariable Long id, @Valid @RequestBody SetSalaryRequest request) {
		return salaryService.set(id, request, currentUser.id());
	}

}

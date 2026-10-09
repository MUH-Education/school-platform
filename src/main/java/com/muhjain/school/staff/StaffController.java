package com.muhjain.school.staff;

import java.util.List;
import java.util.Set;

import com.muhjain.school.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

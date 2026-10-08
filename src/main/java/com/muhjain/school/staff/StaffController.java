package com.muhjain.school.staff;

import java.util.List;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The staff list of the Vehicles and staff screen. Reading needs VEHICLES_VIEW, changing needs VEHICLES_EDIT.
 * People are never deleted: DELETE turns one off.
 */
@Tag(name = "Staff")
@RestController
@RequestMapping("/api/v1/staff")
public class StaffController {

	private final StaffService staffService;

	public StaffController(StaffService staffService) {
		this.staffService = staffService;
	}

	@Operation(summary = "List staff")
	@GetMapping
	@PreAuthorize("hasAuthority('VEHICLES_VIEW')")
	public List<StaffResponse> list() {
		return staffService.list();
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

}

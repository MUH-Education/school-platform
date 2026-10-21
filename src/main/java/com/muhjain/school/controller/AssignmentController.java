package com.muhjain.school.controller;

import com.muhjain.school.model.dto.request.ChangeAssignmentRequest;
import com.muhjain.school.model.dto.response.AssignmentResponse;
import com.muhjain.school.security.CurrentUser;
import com.muhjain.school.service.staff.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Who works on a vehicle. {@code GET /api/v1/vehicles/{id}/assignments} (history) needs VEHICLES_VIEW,
 * {@code POST} (change) needs VEHICLES_EDIT.
 * The URL is under /vehicles, but the code lives in the staff package, next to the assignment rules.
 */
@Tag(name = "Staff")
@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/assignments")
public class AssignmentController {

	private final AssignmentService assignmentService;

	private final CurrentUser currentUser;

	public AssignmentController(AssignmentService assignmentService, CurrentUser currentUser) {
		this.assignmentService = assignmentService;
		this.currentUser = currentUser;
	}

	/** Who worked on the vehicle, newest first. */
	@Operation(summary = "Who drove this vehicle, and when")
	@GetMapping
	@PreAuthorize("hasAuthority('VEHICLES_VIEW')")
	public List<AssignmentResponse> history(@PathVariable Long vehicleId) {
		return assignmentService.history(vehicleId);
	}

	@Operation(summary = "Change the driver or attendant of a vehicle")
	@PostMapping
	@PreAuthorize("hasAuthority('VEHICLES_EDIT')")
	@ResponseStatus(HttpStatus.CREATED)
	public AssignmentResponse change(@PathVariable Long vehicleId,
			@Valid @RequestBody ChangeAssignmentRequest request) {
		return assignmentService.change(vehicleId, request, currentUser.id());
	}

}

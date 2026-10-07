package com.muhjain.school.staff;

import java.util.List;

import com.muhjain.school.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who works on a vehicle. {@code GET /api/v1/vehicles/{id}/assignments} (history) needs VEHICLES_VIEW,
 * {@code POST} (change) needs VEHICLES_EDIT.
 * The URL is under /vehicles, but the code lives in the staff package, next to the assignment rules.
 */
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
	@GetMapping
	@PreAuthorize("hasAuthority('VEHICLES_VIEW')")
	public List<AssignmentResponse> history(@PathVariable Long vehicleId) {
		return assignmentService.history(vehicleId);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('VEHICLES_EDIT')")
	@ResponseStatus(HttpStatus.CREATED)
	public AssignmentResponse change(@PathVariable Long vehicleId,
			@Valid @RequestBody ChangeAssignmentRequest request) {
		return assignmentService.change(vehicleId, request, currentUser.id());
	}

}

package com.muhjain.school.fee;

import com.muhjain.school.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The fees of one child.
 * <ul>
 * <li>{@code PUT /api/v1/students/{id}/fee-plan} — FEES_EDIT</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/students/{studentId}")
public class FeeController {

	private final FeePlanService planService;

	private final CurrentUser currentUser;

	public FeeController(FeePlanService planService, CurrentUser currentUser) {
		this.planService = planService;
		this.currentUser = currentUser;
	}

	@PutMapping("/fee-plan")
	@PreAuthorize("hasAuthority('FEES_EDIT')")
	public FeePlanResponse savePlan(@PathVariable Long studentId, @Valid @RequestBody FeePlanRequest request) {
		return planService.save(studentId, request, currentUser.id());
	}

}

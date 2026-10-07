package com.muhjain.school.fee;

import com.muhjain.school.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The fees of one child.
 * <ul>
 * <li>{@code PUT /api/v1/students/{id}/fee-plan} — FEES_EDIT</li>
 * <li>{@code POST /api/v1/students/{id}/payments} — FEES_EDIT</li>
 * <li>{@code POST /api/v1/students/{id}/payment-corrections} — FEES_CORRECT (owner only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/students/{studentId}")
public class FeeController {

	private final FeePlanService planService;

	private final PaymentService paymentService;

	private final CurrentUser currentUser;

	public FeeController(FeePlanService planService, PaymentService paymentService, CurrentUser currentUser) {
		this.planService = planService;
		this.paymentService = paymentService;
		this.currentUser = currentUser;
	}

	@PutMapping("/fee-plan")
	@PreAuthorize("hasAuthority('FEES_EDIT')")
	public FeePlanResponse savePlan(@PathVariable Long studentId, @Valid @RequestBody FeePlanRequest request) {
		return planService.save(studentId, request, currentUser.id());
	}

	@PostMapping("/payments")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('FEES_EDIT')")
	public ReceiptResponse pay(@PathVariable Long studentId, @Valid @RequestBody PaymentRequest request) {
		return paymentService.record(studentId, request, currentUser.id());
	}

	@PostMapping("/payment-corrections")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('FEES_CORRECT')")
	public ReceiptResponse correct(@PathVariable Long studentId, @Valid @RequestBody CorrectionRequest request) {
		return paymentService.correct(studentId, request, currentUser.id());
	}

}

package com.muhjain.school.controller;

import com.muhjain.school.model.dto.request.CorrectionRequest;
import com.muhjain.school.model.dto.request.FeePlanRequest;
import com.muhjain.school.model.dto.request.PaymentRequest;
import com.muhjain.school.model.dto.response.FeePlanResponse;
import com.muhjain.school.model.dto.response.FeesResponse;
import com.muhjain.school.model.dto.response.ReceiptResponse;
import com.muhjain.school.security.CurrentUser;
import com.muhjain.school.service.fee.FeePlanService;
import com.muhjain.school.service.fee.FeeViewService;
import com.muhjain.school.service.fee.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * The fees of one child.
 * <ul>
 * <li>{@code GET /api/v1/students/{id}/fees} — FEES_VIEW</li>
 * <li>{@code PUT /api/v1/students/{id}/fee-plan} — FEES_EDIT</li>
 * <li>{@code POST /api/v1/students/{id}/payments} — FEES_EDIT</li>
 * <li>{@code POST /api/v1/students/{id}/payment-corrections} — FEES_CORRECT (owner only)</li>
 * </ul>
 */
@Tag(name = "Fees")
@RestController
@RequestMapping("/api/v1/students/{studentId}")
public class FeeController {

	private final FeePlanService planService;

	private final PaymentService paymentService;

	private final FeeViewService viewService;

	private final CurrentUser currentUser;

	public FeeController(FeePlanService planService, PaymentService paymentService, FeeViewService viewService,
			CurrentUser currentUser) {
		this.viewService = viewService;
		this.planService = planService;
		this.paymentService = paymentService;
		this.currentUser = currentUser;
	}

	@Operation(summary = "Fees of one student this year")
	@GetMapping("/fees")
	@PreAuthorize("hasAuthority('FEES_VIEW')")
	public FeesResponse fees(@PathVariable Long studentId) {
		return viewService.view(studentId);
	}

	@Operation(summary = "Create or change the fee plan")
	@PutMapping("/fee-plan")
	@PreAuthorize("hasAuthority('FEES_EDIT')")
	public FeePlanResponse savePlan(@PathVariable Long studentId, @Valid @RequestBody FeePlanRequest request) {
		return planService.save(studentId, request, currentUser.id());
	}

	@Operation(summary = "Record a payment")
	@PostMapping("/payments")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('FEES_EDIT')")
	public ReceiptResponse pay(@PathVariable Long studentId, @Valid @RequestBody PaymentRequest request) {
		return paymentService.record(studentId, request, currentUser.id());
	}

	@Operation(summary = "Correct a wrong payment (owner only)")
	@PostMapping("/payment-corrections")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('FEES_CORRECT')")
	public ReceiptResponse correct(@PathVariable Long studentId, @Valid @RequestBody CorrectionRequest request) {
		return paymentService.correct(studentId, request, currentUser.id());
	}

}

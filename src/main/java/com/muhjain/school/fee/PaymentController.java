package com.muhjain.school.fee;

import java.time.LocalDate;

import com.muhjain.school.common.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/payments?from=2026-10-01&to=2026-10-07&page=0&size=25} — FEES_VIEW.
 * Money received, newest first. Corrections are in the list with a negative amount.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

	private final PaymentQueryService queryService;

	public PaymentController(PaymentQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('FEES_VIEW')")
	public PageResponse<PaymentListItem> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long sessionId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return queryService.list(from, to, sessionId, page, size);
	}

}

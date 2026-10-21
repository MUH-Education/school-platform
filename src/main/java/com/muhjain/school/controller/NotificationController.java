package com.muhjain.school.controller;

import com.muhjain.school.model.dto.request.NotificationRequest;
import com.muhjain.school.model.dto.request.RefineTextRequest;
import com.muhjain.school.model.dto.request.SendNotificationRequest;
import com.muhjain.school.model.dto.response.NotificationRecipientResponse;
import com.muhjain.school.model.dto.response.NotificationReportResponse;
import com.muhjain.school.model.dto.response.NotificationResponse;
import com.muhjain.school.model.dto.response.PageResponse;
import com.muhjain.school.model.dto.response.RefineResult;
import com.muhjain.school.model.dto.response.ResendFailedResponse;
import com.muhjain.school.model.dto.response.TestSendResponse;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.NotificationStatus;
import com.muhjain.school.security.CurrentUser;
import com.muhjain.school.service.notification.NotificationRefineService;
import com.muhjain.school.service.notification.NotificationReportService;
import com.muhjain.school.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Notifications: write, see who gets it, test, send now or later, cancel (docs/10-notifications.md sections 1 and
 * 8). Owner only for now (MARKETING_VIEW / MARKETING_SEND); the kind is checked again inside the service.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {

	private final NotificationService notificationService;

	private final NotificationRefineService refineService;

	private final NotificationReportService reportService;

	private final CurrentUser currentUser;

	public NotificationController(NotificationService notificationService, NotificationRefineService refineService,
			NotificationReportService reportService, CurrentUser currentUser) {
		this.notificationService = notificationService;
		this.refineService = refineService;
		this.reportService = reportService;
		this.currentUser = currentUser;
	}

	/**
	 * The AI "Refine" helper: a suggestion only, nothing is saved. Every {box} must survive (409 otherwise).
	 * 409 REFINE_NOT_CONFIGURED when the server has no AI key.
	 */
	@Operation(summary = "Suggest a better text (AI)")
	@PostMapping("/refine")
	@PreAuthorize("hasAuthority('MARKETING_SEND')")
	public RefineResult refine(@Valid @RequestBody RefineTextRequest request) {
		return refineService.refine(request);
	}

	/** Home and Reports list, newest first. Every filter is optional. */
	@Operation(summary = "List notifications")
	@GetMapping
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public PageResponse<NotificationResponse> list(@RequestParam(required = false) NotificationKind kind,
			@RequestParam(required = false) NotificationStatus status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
		return notificationService.list(kind, status, from, to, page, size);
	}

	/** One notification. A draft or scheduled one comes with the counts of who would get it now. */
	@Operation(summary = "One notification")
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public NotificationResponse get(@PathVariable Long id) {
		return notificationService.get(id);
	}

	/** A new draft. Answers the counts of "Who gets it". Nothing is sent. */
	@Operation(summary = "Make a notification (draft)")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('MARKETING_SEND')")
	public NotificationResponse create(@Valid @RequestBody NotificationRequest request) {
		return notificationService.create(request, currentUser.role().permissions(), currentUser.id());
	}

	@Operation(summary = "Change a draft or scheduled notification")
	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('MARKETING_SEND')")
	public NotificationResponse update(@PathVariable Long id, @Valid @RequestBody NotificationRequest request) {
		return notificationService.update(id, request, currentUser.role().permissions(), currentUser.id());
	}

	/** "See the 246 families": who would get it now, or (once sent) each row with its status. Phones masked. */
	@Operation(summary = "Who gets it")
	@GetMapping("/{id}/recipients")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public PageResponse<NotificationRecipientResponse> recipients(@PathVariable Long id,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
		return notificationService.recipients(id, page, size);
	}

	/** One copy to your own phone, with the example values. */
	@Operation(summary = "Send a test to my phone")
	@PostMapping("/{id}/test")
	@PreAuthorize("hasAuthority('MARKETING_SEND')")
	public TestSendResponse test(@PathVariable Long id) {
		return notificationService.test(id, currentUser.role().permissions(), currentUser.id());
	}

	/** {@code {"sendNow": true}} or {@code {"scheduledAt": "..."}}. The rows are written within a minute. */
	@Operation(summary = "Send now or later")
	@PostMapping("/{id}/send")
	@PreAuthorize("hasAuthority('MARKETING_SEND')")
	public NotificationResponse send(@PathVariable Long id, @RequestBody SendNotificationRequest request) {
		return notificationService.send(id, request, currentUser.role().permissions(), currentUser.id());
	}

	/** Counts by status, channel, skip reason, failure reason and button, and reads per hour. Never stored. */
	@Operation(summary = "Report of a notification")
	@GetMapping("/{id}/report")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public NotificationReportResponse report(@PathVariable Long id) {
		return reportService.report(id);
	}

	/** The failed messages worth a second try go back to the queue (the same rows). */
	@Operation(summary = "Send the failed messages again")
	@PostMapping("/{id}/resend-failed")
	@PreAuthorize("hasAuthority('MARKETING_SEND')")
	public ResendFailedResponse resendFailed(@PathVariable Long id) {
		return reportService.resendFailed(id, currentUser.role().permissions(), currentUser.id());
	}

	/** Cancel a draft or scheduled one; a sending one stops what is not sent yet. */
	@Operation(summary = "Cancel a notification")
	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('MARKETING_SEND')")
	public NotificationResponse cancel(@PathVariable Long id) {
		return notificationService.cancel(id, currentUser.role().permissions(), currentUser.id());
	}

}

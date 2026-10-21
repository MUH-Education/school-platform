package com.muhjain.school.controller;

import com.muhjain.school.model.dto.request.NotificationTemplateRequest;
import com.muhjain.school.model.dto.request.TemplateApprovalRequest;
import com.muhjain.school.model.dto.response.NotificationTemplateResponse;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.security.CurrentUser;
import com.muhjain.school.service.notification.NotificationTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Notification templates, the office's "saved texts" (docs/10-notifications.md sections 4 and 8).
 * Owner only for now (MARKETING_VIEW / MARKETING_EDIT, docs/08-decisions.md).
 */
@RestController
@RequestMapping("/api/v1/notification-templates")
@Tag(name = "Notifications")
public class NotificationTemplateController {

	private final NotificationTemplateService templateService;

	private final CurrentUser currentUser;

	public NotificationTemplateController(NotificationTemplateService templateService, CurrentUser currentUser) {
		this.templateService = templateService;
		this.currentUser = currentUser;
	}

	/** The templates in use, with their WhatsApp approval. {@code ?kind=MARKETING} for one kind. */
	@Operation(summary = "List notification templates")
	@GetMapping
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public List<NotificationTemplateResponse> list(@RequestParam(required = false) NotificationKind kind) {
		return templateService.list(kind);
	}

	@Operation(summary = "One notification template")
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public NotificationTemplateResponse get(@PathVariable Long id) {
		return templateService.get(id);
	}

	/** A new template. It waits for WhatsApp's approval before it can be sent. */
	@Operation(summary = "Make a notification template")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('MARKETING_EDIT')")
	public NotificationTemplateResponse create(@Valid @RequestBody NotificationTemplateRequest request) {
		return templateService.create(request, currentUser.id());
	}

	/** Change a template. An approved one becomes a new version that waits for approval again. */
	@Operation(summary = "Change a notification template")
	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('MARKETING_EDIT')")
	public NotificationTemplateResponse update(@PathVariable Long id,
			@Valid @RequestBody NotificationTemplateRequest request) {
		return templateService.update(id, request, currentUser.id());
	}

	/** Local and test only: the owner types WhatsApp's answer. In prod the provider sends it (404 here). */
	@Operation(summary = "Approve or reject a template by hand (local/test only)")
	@PutMapping("/{id}/approval")
	@PreAuthorize("hasAuthority('MARKETING_EDIT')")
	public NotificationTemplateResponse decide(@PathVariable Long id,
			@Valid @RequestBody TemplateApprovalRequest request) {
		return templateService.decide(id, request, currentUser.id());
	}

}

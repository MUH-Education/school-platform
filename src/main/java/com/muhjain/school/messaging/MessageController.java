package com.muhjain.school.messaging;

import java.time.LocalDate;
import java.util.List;

import com.muhjain.school.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Messages screen. Reading needs MESSAGES_VIEW. Changing a text needs SETTINGS_EDIT (the owner), because every
 * text must match what the provider approved. Phones in answers are masked.
 */
@RestController
@RequestMapping("/api/v1")
public class MessageController {

	private final MessageService messageService;

	public MessageController(MessageService messageService) {
		this.messageService = messageService;
	}

	/** The SMS log of one day (default today), newest first. Filters: {@code status}, {@code studentId}, {@code phone}. */
	@GetMapping("/messages")
	@PreAuthorize("hasAuthority('MESSAGES_VIEW')")
	public PageResponse<MessageResponse> messages(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) MessageStatus status, @RequestParam(required = false) Long studentId,
			@RequestParam(required = false) String phone, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return messageService.list(date, status, studentId, phone, page, size);
	}

	@GetMapping("/messages/summary")
	@PreAuthorize("hasAuthority('MESSAGES_VIEW')")
	public MessageSummaryResponse summary(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return messageService.summary(date);
	}

	@GetMapping("/message-templates")
	@PreAuthorize("hasAuthority('MESSAGES_VIEW')")
	public List<TemplateResponse> templates() {
		return messageService.templates();
	}

	@PutMapping("/message-templates/{code}")
	@PreAuthorize("hasAuthority('SETTINGS_EDIT')")
	public TemplateResponse updateTemplate(@PathVariable String code, @Valid @RequestBody UpdateTemplateRequest request) {
		return messageService.updateTemplate(code, request);
	}

}

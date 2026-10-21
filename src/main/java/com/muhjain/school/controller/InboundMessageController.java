package com.muhjain.school.controller;

import com.muhjain.school.model.dto.request.HandleInboundRequest;
import com.muhjain.school.model.dto.response.InboundMessageResponse;
import com.muhjain.school.model.dto.response.PageResponse;
import com.muhjain.school.security.CurrentUser;
import com.muhjain.school.service.notification.InboundMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Replies that came in to the school number (docs/10-notifications.md section 8). STOP is handled by the server;
 * the rest waits for a person. The full inbox stays the provider's for now: this is the short list and "done".
 */
@RestController
@RequestMapping("/api/v1/inbound-messages")
@Tag(name = "Notifications")
public class InboundMessageController {

	private final InboundMessageService inboundMessages;

	private final CurrentUser currentUser;

	public InboundMessageController(InboundMessageService inboundMessages, CurrentUser currentUser) {
		this.inboundMessages = inboundMessages;
		this.currentUser = currentUser;
	}

	/** Newest first. {@code ?handled=false} = the ones waiting for a person. */
	@Operation(summary = "Replies to the school number")
	@GetMapping
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public PageResponse<InboundMessageResponse> list(@RequestParam(required = false) Boolean handled,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
		return inboundMessages.list(handled, page, size);
	}

	/** Done, with a note, a "promised to pay by" date, or a new enquiry. */
	@Operation(summary = "Mark a reply as done")
	@PostMapping("/{id}/handle")
	@PreAuthorize("hasAuthority('MARKETING_SEND')")
	public InboundMessageResponse handle(@PathVariable Long id, @Valid @RequestBody HandleInboundRequest request) {
		return inboundMessages.handle(id, request, currentUser.role().permissions(), currentUser.id());
	}

}

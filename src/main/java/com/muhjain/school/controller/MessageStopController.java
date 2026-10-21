package com.muhjain.school.controller;

import com.muhjain.school.model.dto.response.MessageStopResponse;
import com.muhjain.school.model.dto.response.PageResponse;
import com.muhjain.school.service.notification.StopListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * The stop list: families who said "stop" to marketing and greetings (docs/10-notifications.md section 8).
 * A family gets on it by itself (STOP reply); the office can only take a family off, when they ask.
 */
@RestController
@RequestMapping("/api/v1/message-stops")
@Tag(name = "Notifications")
public class MessageStopController {

	private final StopListService stopList;

	public MessageStopController(StopListService stopList) {
		this.stopList = stopList;
	}

	@Operation(summary = "The stop list")
	@GetMapping
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public PageResponse<MessageStopResponse> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return stopList.list(page, size);
	}

	/** The family asked to get the school's news again. */
	@Operation(summary = "Take a family off the stop list")
	@DeleteMapping("/{guardianId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('MARKETING_SEND')")
	public void remove(@PathVariable Long guardianId) {
		stopList.remove(guardianId);
	}

}

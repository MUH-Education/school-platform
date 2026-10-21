package com.muhjain.school.controller;

import com.muhjain.school.model.dto.response.NotificationMediaResponse;
import com.muhjain.school.service.notification.NotificationMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload a file for a notification template or a notification (docs/10-notifications.md section 8).
 * For now only the owner (MARKETING_EDIT or MARKETING_SEND, docs/08-decisions.md).
 */
@RestController
@RequestMapping("/api/v1/notification-media")
@Tag(name = "Notifications")
public class NotificationMediaController {

	private final NotificationMediaService mediaService;

	public NotificationMediaController(NotificationMediaService mediaService) {
		this.mediaService = mediaService;
	}

	/**
	 * Multipart field {@code file}: a JPEG/PNG picture (5 MB), MP4 video (16 MB), PDF (5 MB) or audio (16 MB).
	 * Answers the key to send with the template or notification.
	 */
	@Operation(summary = "Upload a poster, video, PDF or audio file")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyAuthority('MARKETING_EDIT', 'MARKETING_SEND')")
	public NotificationMediaResponse upload(@RequestParam("file") MultipartFile file) {
		return mediaService.save(file);
	}

}

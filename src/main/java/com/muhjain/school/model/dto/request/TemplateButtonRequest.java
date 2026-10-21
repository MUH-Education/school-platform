package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.ButtonType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One WhatsApp button. {@code action}: for REPLY one of STOP_MESSAGES, BOOK_VISIT, GOT_IT, CALL_ME; for CALL a
 * mobile number; for URL an https link. Example: {@code {"type":"URL","text":"See photos","action":"https://..."}}.
 */
public record TemplateButtonRequest(@NotNull ButtonType type, @NotBlank @Size(max = 25) String text,
		@NotBlank @Size(max = 300) String action) {
}

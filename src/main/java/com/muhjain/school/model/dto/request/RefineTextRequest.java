package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.RefineAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Ask the AI for a better text. {@code instruction} is needed for CUSTOM ("make it sound like the principal").
 * {@code language}: {@code hi} (default) or {@code en}. Example:
 * {@code {"text":"नमस्ते {Parent name}, ...","action":"SHORTER"}}.
 */
public record RefineTextRequest(@NotBlank @Size(max = 1024) String text, @NotNull RefineAction action,
		@Size(max = 300) String instruction, @Pattern(regexp = "hi|en") String language) {
}

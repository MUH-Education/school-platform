package com.muhjain.school.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The whole template to save: the text and the provider's id. Example:
 * {@code {"body":"{name} स्कूल पहुँच गया — {time}। MUH Jain School","providerTemplateId":"1107160000000000001","active":true}}.
 * The text must keep {@code {name}} and {@code {time}}.
 */
public record UpdateTemplateRequest(@NotBlank @Size(max = 500) String body,
		@Size(max = 60) String providerTemplateId, @NotNull Boolean active) {

}

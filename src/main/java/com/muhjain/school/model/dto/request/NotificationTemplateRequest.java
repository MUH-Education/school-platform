package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.HeaderType;
import com.muhjain.school.model.enums.NotificationKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * A notification template, new or changed. The kind is fixed when it is made.
 * <ul>
 * <li>{@code language}: {@code hi} (default) or {@code en}.</li>
 * <li>{@code headerType}: NONE (default), IMAGE, VIDEO or DOCUMENT; then {@code mediaKey} is the file from
 * {@code POST /notification-media}.</li>
 * <li>{@code buttons}: at most 3. MARKETING and GREETING get "Stop messages" as the last one from the server, so
 * at most 2 of your own.</li>
 * <li>{@code exampleValues}: one example per {box} of the texts (WhatsApp asks for it).</li>
 * </ul>
 * Example: {@code {"name":"Open day","kind":"MARKETING","body":"नमस्ते {Parent name}, रविवार को open day है।",
 * "headerType":"IMAGE","mediaKey":"notifications/3f2a.jpg","buttons":[{"type":"CALL","text":"Call school",
 * "action":"9800000100"}],"exampleValues":{"Parent name":"Rajesh Punia"}}}
 */
public record NotificationTemplateRequest(@NotBlank @Size(max = 60) String name, @NotNull NotificationKind kind,
		@Pattern(regexp = "hi|en") String language, @NotBlank @Size(max = 1000) String body,
		@Size(max = 320) String smsBody, HeaderType headerType, @Size(max = 120) String mediaKey,
		@Size(max = 3) List<@NotNull @Valid TemplateButtonRequest> buttons, Map<String, String> exampleValues) {
}

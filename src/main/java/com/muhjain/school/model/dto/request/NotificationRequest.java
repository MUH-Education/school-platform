package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.enums.NotificationKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * A notification draft, new or changed (docs/10-notifications.md sections 1 to 3).
 * <ul>
 * <li>{@code templateId}: an approved template of the same kind. Its text is what is sent.</li>
 * <li>{@code body}: only for ONE_FAMILY and STAFF with a template that is not approved (free text inside the
 * 24-hour window). Otherwise leave it out: the template's text is used.</li>
 * <li>{@code smsBody}: the SMS text (null = the template's).</li>
 * <li>{@code fixedValues}: values that are the same for everyone, for every {box} the recipients do not fill.
 * Example: {"Date": "10 अक्टूबर"}.</li>
 * <li>{@code mediaKey}: another file of the template's header type (null = the template's own file).</li>
 * </ul>
 * Example: {@code {"name":"PTM Class 10","kind":"NOTICE","templateId":31,"fixedValues":{"Date":"10 अक्टूबर"},
 * "audience":{"groups":["CURRENT_PARENTS"],"filter":{"classes":["10"]}}}}
 */
public record NotificationRequest(@NotBlank @Size(max = 80) String name, @NotNull NotificationKind kind,
		@NotNull Long templateId, @Size(max = 1024) String body, @Size(max = 320) String smsBody,
		Map<String, String> fixedValues, @Size(max = 120) String mediaKey, @NotNull Audience audience) {
}

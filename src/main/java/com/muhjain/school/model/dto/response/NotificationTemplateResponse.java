package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.HeaderType;
import com.muhjain.school.model.enums.TemplateApproval;
import com.muhjain.school.model.enums.TemplateKind;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * A notification template as the Templates screen shows it. The office calls it a "saved text"; the approval is
 * WhatsApp's answer. {@code replacesId} = the approved template this one is a new version of.
 * Example: 31, "Open day", MARKETING, hi, WAITING, buttons [Call school, Stop messages].
 */
public record NotificationTemplateResponse(Long id, String name, TemplateKind kind, String language, String body,
		String smsBody, HeaderType headerType, String mediaKey, List<TemplateButtonResponse> buttons,
		Map<String, String> exampleValues, TemplateApproval approval, String rejectionReason, Long replacesId,
		boolean active, OffsetDateTime updatedAt) {
}

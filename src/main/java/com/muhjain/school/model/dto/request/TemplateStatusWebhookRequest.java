package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.TemplateApproval;

/**
 * Provider callback: WhatsApp approved or rejected a template. {@code templateName} is the template's
 * {@code providerTemplateName}. Example: {@code {"templateName":"open_day_oct","status":"REJECTED",
 * "reason":"Too promotional"}}.
 */
public record TemplateStatusWebhookRequest(String templateName, TemplateApproval status, String reason) {
}

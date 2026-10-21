package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.TemplateApproval;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The provider's answer, typed by the owner (local/test only). {@code reason} is needed for REJECTED.
 * Example: {@code {"approval":"REJECTED","reason":"Too promotional for a utility template"}}.
 */
public record TemplateApprovalRequest(@NotNull TemplateApproval approval, @Size(max = 300) String reason) {
}

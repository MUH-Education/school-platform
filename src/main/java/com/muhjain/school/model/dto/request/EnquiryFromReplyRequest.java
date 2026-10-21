package com.muhjain.school.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What an enquiry needs that a WhatsApp reply does not give: the office types it while handling the reply. The phone
 * comes from the reply, the source is WHATSAPP. Example: {@code {"parentName":"Suresh Malik","childName":"Riya",
 * "classSought":"1","village":"Tohana"}}.
 */
public record EnquiryFromReplyRequest(@NotBlank @Size(max = 120) String parentName,
		@Size(max = 120) String childName, @NotBlank String classSought, @NotBlank @Size(max = 80) String village) {
}

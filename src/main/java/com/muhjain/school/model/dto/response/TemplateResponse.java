package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.entity.MessageTemplate;
import com.muhjain.school.model.enums.Gender;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.TemplateApproval;

/**
 * A template as the office sees it. {@code gender} is null for a fee text (one row), M or F for a bus text
 * (two rows share a {@code code}, edited separately). {@code providerTemplateId} is filled after DLT approval.
 */
public record TemplateResponse(Long id, String code, Gender gender, String name, MessageChannel channel,
		String language, String body, String approvedBody, TemplateApproval approval, String providerTemplateId,
		boolean active) {

	public static TemplateResponse of(MessageTemplate t) {
		return new TemplateResponse(t.getId(), t.getCode(), t.getGender(), t.getName(), t.getChannel(),
				t.getLanguage(), t.getBody(), t.getApprovedBody(), t.getApproval(), t.getProviderTemplateId(),
				t.isActive());
	}

}

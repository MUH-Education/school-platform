package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.entity.MessageTemplate;
import com.muhjain.school.model.enums.MessageChannel;

/** A template as the office sees it. {@code providerTemplateId} is filled after DLT approval. */
public record TemplateResponse(String code, MessageChannel channel, String language, String body,
		String providerTemplateId, boolean active) {

	public static TemplateResponse of(MessageTemplate t) {
	return new TemplateResponse(t.getCode(), t.getChannel(), t.getLanguage(), t.getBody(),
				t.getProviderTemplateId(), t.isActive());
	}

}

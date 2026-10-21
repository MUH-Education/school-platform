package com.muhjain.school.service.messaging.impl;

import com.muhjain.school.exception.SmsSendException;
import com.muhjain.school.model.dto.response.SendContext;
import com.muhjain.school.model.dto.response.SendResult;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.service.messaging.ChannelSender;
import com.muhjain.school.service.messaging.ProviderApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * The real WhatsApp sender, used when {@code app.messaging.whatsapp-provider} is anything but {@code log}. It only
 * passes the row to the one {@link ProviderApi} class: an approved template with its values in order, or free text
 * inside the 24-hour window. Nothing about a provider is here.
 */
@Component
@ConditionalOnExpression("!'${app.messaging.whatsapp-provider:log}'.trim().equalsIgnoreCase('log')")
public class WhatsAppChannelSender implements ChannelSender {

	private final ObjectProvider<ProviderApi> api;

	public WhatsAppChannelSender(ObjectProvider<ProviderApi> api) {
		this.api = api;
	}

	@Override
	public MessageChannel channel() {
		return MessageChannel.WHATSAPP;
	}

	@Override
	public SendResult send(MessageOutbox row, SendContext context) throws SmsSendException {
		ProviderApi provider = api.getIfAvailable();
		if (provider == null) {
			throw new SmsSendException("No provider class (ProviderApi) is installed");
		}
		if (context.freeText() || context.templateName() == null) {
			if (!context.freeText()) {
				throw new SmsSendException("The template has no provider template name");
			}
			return new SendResult(provider.sendWhatsAppText(row.getPhone(), row.getBody(), context.mediaId()), false);
		}
		return new SendResult(provider.sendWhatsAppTemplate(row.getPhone(), context.templateName(), context.language(),
				context.params(), context.mediaId()), false);
	}

}

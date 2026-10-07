package com.muhjain.school.messaging;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * The real parent SMS sender. It is used when {@code app.messaging.sms-provider} is anything but {@code log}, and it
 * only passes the call to the one {@link ProviderApi} class. Nothing about a provider is here.
 */
@Component
@ConditionalOnExpression("!'${app.messaging.sms-provider:log}'.trim().equalsIgnoreCase('log')")
public class ProviderSmsSender implements SmsSender {

	private final ObjectProvider<ProviderApi> api;

	public ProviderSmsSender(ObjectProvider<ProviderApi> api) {
		this.api = api;
	}

	@Override
	public SendResult send(String phone, String body, String providerTemplateId) throws SmsSendException {
		ProviderApi provider = api.getIfAvailable();
		if (provider == null) {
			throw new SmsSendException("No provider class (ProviderApi) is installed");
		}
		return new SendResult(provider.sendSms(phone, body, providerTemplateId), false);
	}

}

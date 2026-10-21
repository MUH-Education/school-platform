package com.muhjain.school.service.auth.impl;

import com.muhjain.school.exception.OtpSendException;
import com.muhjain.school.exception.SmsSendException;
import com.muhjain.school.service.auth.OtpSender;
import com.muhjain.school.service.messaging.ProviderApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Sends the login code as a WhatsApp authentication message, through the one {@link ProviderApi} class. */
@Component
public class WhatsAppOtpSender implements OtpSender {

	private final ObjectProvider<ProviderApi> api;

	public WhatsAppOtpSender(ObjectProvider<ProviderApi> api) {
		this.api = api;
	}

	@Override
	public String channel() {
		return "WHATSAPP";
	}

	@Override
	public void send(String phone, String code) throws OtpSendException {
		ProviderApi provider = api.getIfAvailable();
		if (provider == null) {
			throw new OtpSendException("No provider class (ProviderApi) is installed");
		}
		try {
			provider.sendWhatsAppOtp(phone, code);
		}
		catch (SmsSendException ex) {
			throw new OtpSendException("WhatsApp: " + ex.getMessage());
		}
	}

}

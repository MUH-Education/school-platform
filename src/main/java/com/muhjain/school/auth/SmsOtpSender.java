package com.muhjain.school.auth;

import com.muhjain.school.messaging.ProviderApi;
import com.muhjain.school.messaging.SmsSendException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Sends the login code as an SMS with the approved OTP template, through the one {@link ProviderApi} class. */
@Component
public class SmsOtpSender implements OtpSender {

	private final ObjectProvider<ProviderApi> api;

	public SmsOtpSender(ObjectProvider<ProviderApi> api) {
		this.api = api;
	}

	@Override
	public String channel() {
		return "SMS";
	}

	@Override
	public void send(String phone, String code) throws OtpSendException {
		ProviderApi provider = api.getIfAvailable();
		if (provider == null) {
			throw new OtpSendException("No provider class (ProviderApi) is installed");
		}
		try {
			provider.sendSmsOtp(phone, code);
		}
		catch (SmsSendException ex) {
			throw new OtpSendException("SMS: " + ex.getMessage());
		}
	}

}

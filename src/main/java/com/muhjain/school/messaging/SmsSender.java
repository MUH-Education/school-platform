package com.muhjain.school.messaging;

/**
 * One way to send a parent SMS. To change the SMS company later, write one new class. Nothing else changes.
 * The class is chosen by {@code app.messaging.sms-provider} (log | the real provider).
 */
public interface SmsSender {

	/**
	 * @param phone {@code +91XXXXXXXXXX}
	 * @param body the final text (must match the DLT-approved text)
	 * @param providerTemplateId the id the provider gave for the template, may be null in test mode
	 * @throws SmsSendException if the provider did not take it
	 */
	SendResult send(String phone, String body, String providerTemplateId) throws SmsSendException;

}

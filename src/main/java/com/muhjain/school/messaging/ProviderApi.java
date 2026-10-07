package com.muhjain.school.messaging;

/**
 * THE ONE CLASS TO WRITE PER PROVIDER (question C1). Everything else is ready: the queue, the worker, retries,
 * masking, the OTP fallback and the start-up checks.
 * <p>
 * To add a provider, write one {@code @Component} that implements this interface (for example {@code Msg91Api}),
 * keep every provider detail inside it (URL, headers, JSON, template ids), and set
 * {@code app.messaging.sms-provider} to any name except {@code log}. Read the keys from
 * {@link ProviderProperties} (environment variables, never in the repo). Use {@link ProviderHttp#client} so the call
 * has the 5 second timeout (rule 13).
 * <p>
 * Throw {@link SmsSendException} when the provider says no or does not answer. The message is saved in
 * {@code message_outbox.error} and the log, so never put a key or a full phone number in it.
 */
public interface ProviderApi {

	/**
	 * A parent SMS with a DLT template (the 8 Hindi texts).
	 *
	 * @param phone {@code +91XXXXXXXXXX}
	 * @param body the final text, equal to the approved template filled with name and time
	 * @param providerTemplateId the DLT template id saved in {@code message_template}
	 * @return the provider's id for the message, or null
	 */
	String sendSms(String phone, String body, String providerTemplateId) throws SmsSendException;

	/** The login code as a WhatsApp authentication template. */
	void sendWhatsAppOtp(String phone, String code) throws SmsSendException;

	/** The login code as an SMS with the approved OTP template. */
	void sendSmsOtp(String phone, String code) throws SmsSendException;

}

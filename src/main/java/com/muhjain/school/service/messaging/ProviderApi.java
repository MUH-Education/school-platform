package com.muhjain.school.service.messaging;

import com.muhjain.school.config.ProviderProperties;
import com.muhjain.school.exception.SmsSendException;
import com.muhjain.school.service.messaging.impl.ProviderHttp;


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

	/**
	 * A notification as an approved WhatsApp template to one phone (Phase 11).
	 * <p>
	 * Throw {@link SmsSendException} with problem {@code NOT_ON_WHATSAPP} when the number has no WhatsApp: the worker
	 * then sends the notification's SMS text instead.
	 *
	 * @param phone {@code +91XXXXXXXXXX}
	 * @param templateName the approved template's name at the provider, from {@code message_template}. Example:
	 * {@code open_day_oct}
	 * @param language the template's language. Example: {@code hi}
	 * @param params the values of the template's {boxes}, in the order they appear in its text ({{1}}, {{2}}, ...)
	 * @param mediaId the header file's id from {@link #uploadMedia}, or null for a text-only template
	 * @return the provider's id for the message, or null
	 */
	String sendWhatsAppTemplate(String phone, String templateName, String language, java.util.List<String> params,
			String mediaId) throws SmsSendException;

	/**
	 * Free text on WhatsApp (no template). WhatsApp allows it only inside the 24-hour window, after the person wrote
	 * to the school; the guard chain has checked that already.
	 *
	 * @param mediaId a file from {@link #uploadMedia} to send with it, or null
	 * @return the provider's id for the message, or null
	 */
	String sendWhatsAppText(String phone, String text, String mediaId) throws SmsSendException;

	/**
	 * Uploads a file once, so many messages can point at it. The provider keeps it about 30 days.
	 *
	 * @param contentType example {@code video/mp4}
	 * @param fileName a name for the provider, example {@code 9b1e.mp4} (never a person's name)
	 * @return the provider's id for the file
	 */
	String uploadMedia(byte[] bytes, String contentType, String fileName) throws SmsSendException;

}

package com.muhjain.school.service.messaging;

import com.muhjain.school.config.ProviderProperties;
import com.muhjain.school.exception.SmsSendException;
import com.muhjain.school.service.messaging.impl.ProviderHttp;

import java.util.Map;

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
	 * A Marketing/Notice WhatsApp template to one phone, for a broadcast (Phase 11, not built yet —
	 * see docs/09-marketing.md). Added early so the shape is ready; no real class implements it yet (question C1).
	 *
	 * @param phone {@code +91XXXXXXXXXX}
	 * @param providerTemplateId the approved WhatsApp template id, from {@code message_template}
	 * @param params placeholder number to value, for example {@code {"1": "Rajesh Punia"}}; empty if the template
	 * has no placeholders
	 * @param mediaUrl the template's poster image, or null if it has none
	 * @return the provider's id for the message, or null
	 */
	String sendWhatsAppTemplate(String phone, String providerTemplateId, Map<String, String> params, String mediaUrl)
			throws SmsSendException;

	/**
	 * A text or media message into one existing, small WhatsApp group (Phase 11, not built yet, question C12 —
	 * see docs/09-marketing.md). Not for parent broadcasts: group size is too small and buttons do not work inside
	 * a group.
	 *
	 * @param groupId the provider's own id for the group, saved on {@code whatsapp_group.provider_group_id}
	 * @param text the message text
	 * @param mediaUrl an image or document to attach, or null for text only
	 * @return the provider's id for the message, or null
	 */
	String sendWhatsAppGroupMessage(String groupId, String text, String mediaUrl) throws SmsSendException;

}

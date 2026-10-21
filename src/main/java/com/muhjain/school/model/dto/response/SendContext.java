package com.muhjain.school.model.dto.response;

import java.util.List;

/**
 * What a {@code ChannelSender} needs besides the outbox row itself.
 * Example (a notification on WhatsApp): template {@code open_day_oct}, language hi, params ["Rajesh Punia",
 * "Aryan और Siya"], media id "1045...".
 *
 * @param smsTemplateId the SMS (DLT) template id, or null
 * @param templateName the WhatsApp template's name at the provider, or null
 * @param language the WhatsApp template's language, or null
 * @param params the values of the WhatsApp template's {boxes}, in order
 * @param freeText true = send the row's text as free WhatsApp text, not as a template (inside the 24-hour window)
 * @param mediaId the provider's id of the header file, or null
 */
public record SendContext(String smsTemplateId, String templateName, String language, List<String> params,
		boolean freeText, String mediaId) {

	public SendContext {
		params = (params == null) ? List.of() : List.copyOf(params);
	}

	/** A plain SMS with this DLT template id (bus SMS, receipts). */
	public static SendContext sms(String smsTemplateId) {
		return new SendContext(smsTemplateId, null, null, List.of(), false, null);
	}

}

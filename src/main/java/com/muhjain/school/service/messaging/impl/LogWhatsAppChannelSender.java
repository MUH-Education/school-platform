package com.muhjain.school.service.messaging.impl;

import com.muhjain.school.model.dto.response.SendContext;
import com.muhjain.school.model.dto.response.SendResult;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.service.messaging.ChannelSender;
import com.muhjain.school.util.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Test mode for WhatsApp: one log line per row, nothing leaves the server, the row becomes TEST_ONLY. Used when
 * {@code app.messaging.whatsapp-provider} is {@code log} (local and test). The phone is masked and the text is not
 * logged (a notification can hold a family's fee numbers).
 * Example: {@code WhatsApp to +91XXXXXX0001: template open_day_oct (hi), 2 values, media yes}.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.whatsapp-provider", havingValue = "log", matchIfMissing = true)
public class LogWhatsAppChannelSender implements ChannelSender {

	private static final Logger log = LoggerFactory.getLogger(LogWhatsAppChannelSender.class);

	@Override
	public MessageChannel channel() {
		return MessageChannel.WHATSAPP;
	}

	@Override
	public SendResult send(MessageOutbox row, SendContext context) {
		String what = context.freeText() ? "free text" : "template " + context.templateName() + " ("
				+ context.language() + ")";
		log.info("WhatsApp to {}: {}, {} values, media {}", PhoneNumbers.mask(row.getPhone()), what,
				context.params().size(), (context.mediaId() == null) ? "no" : "yes");
		return new SendResult(null, true);
	}

}

package com.muhjain.school.service.messaging.impl;

import com.muhjain.school.exception.SmsSendException;
import com.muhjain.school.model.dto.response.SendContext;
import com.muhjain.school.model.dto.response.SendResult;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.service.messaging.ChannelSender;
import com.muhjain.school.service.messaging.SmsSender;
import org.springframework.stereotype.Component;

/**
 * SMS rows: passed to the existing {@link SmsSender} (the log sender, or the provider). No change for bus SMS: the
 * final text and the DLT template id, as before.
 */
@Component
public class SmsChannelSender implements ChannelSender {

	private final SmsSender smsSender;

	public SmsChannelSender(SmsSender smsSender) {
		this.smsSender = smsSender;
	}

	@Override
	public MessageChannel channel() {
		return MessageChannel.SMS;
	}

	@Override
	public SendResult send(MessageOutbox row, SendContext context) throws SmsSendException {
		return smsSender.send(row.getPhone(), row.getBody(), context.smsTemplateId());
	}

}

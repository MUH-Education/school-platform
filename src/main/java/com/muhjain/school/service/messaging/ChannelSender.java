package com.muhjain.school.service.messaging;

import com.muhjain.school.exception.SmsSendException;
import com.muhjain.school.model.dto.response.SendContext;
import com.muhjain.school.model.dto.response.SendResult;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.enums.MessageChannel;

/**
 * Sends one outbox row on one channel. {@code OutboxWorker} picks the sender by {@code row.getChannel()} from the
 * Spring beans, never with a {@code switch}. To add a channel (email, app push): one {@code MessageChannel} value
 * and one class. Example: a WHATSAPP row of notification 17 goes to the WhatsApp sender, a bus SMS to the SMS one.
 */
public interface ChannelSender {

	MessageChannel channel();

	/**
	 * @throws SmsSendException when the provider did not take it; with problem NOT_ON_WHATSAPP the worker sends an
	 * SMS instead
	 */
	SendResult send(MessageOutbox row, SendContext context) throws SmsSendException;

}

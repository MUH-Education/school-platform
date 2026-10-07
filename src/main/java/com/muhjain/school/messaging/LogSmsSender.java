package com.muhjain.school.messaging;

import com.muhjain.school.common.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Test mode: writes the SMS to the log and sends nothing. The outbox row becomes TEST_ONLY.
 * Used when {@code app.messaging.sms-provider} is {@code log} (the default in dev and test).
 * The phone is masked in the log (security rule 5).
 * Example log line: {@code SMS to +91XXXXXX0001: Aryan सुबह की बस में चढ़ गया — 7:42। MUH Jain School}.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.sms-provider", havingValue = "log", matchIfMissing = true)
public class LogSmsSender implements SmsSender {

	private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

	@Override
	public SendResult send(String phone, String body, String providerTemplateId) {
		log.info("SMS to {}: {}", PhoneNumbers.mask(phone), body);
		return new SendResult(null, true);
	}

}

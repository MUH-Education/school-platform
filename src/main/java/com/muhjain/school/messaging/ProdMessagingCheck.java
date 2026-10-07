package com.muhjain.school.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Start-up check for {@code prod} (rule 14). With the {@code log} SMS provider every parent SMS would be written to
 * a log and nobody would get it, so the app refuses to start. (The same rule for the login code, "only the log
 * channel", is checked in {@code OtpDeliveryService}.)
 * Example: {@code app.messaging.sms-provider: log} with the prod profile → the app stops with a clear message.
 */
@Component
public class ProdMessagingCheck {

	public ProdMessagingCheck(Environment environment,
			@Value("${app.messaging.sms-provider:log}") String smsProvider) {
		if (environment.matchesProfiles("prod") && "log".equalsIgnoreCase(smsProvider.trim())) {
			throw new IllegalStateException("In prod app.messaging.sms-provider cannot be 'log'. "
					+ "Parents would never get an SMS. Set it to the real provider.");
		}
	}

}

package com.muhjain.school.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the real provider, under {@code app.messaging.provider}. Secrets come from environment variables.
 * Example: {@code APP_PROVIDER_AUTH_KEY}, {@code APP_PROVIDER_SENDER_ID}.
 *
 * @param baseUrl the provider's API address
 * @param authKey the API key (secret)
 * @param senderId the DLT sender id (header) for SMS
 * @param otpSmsTemplateId the approved DLT template id for the login code by SMS
 * @param whatsappOtpTemplate the approved WhatsApp authentication template name
 * @param timeout connect and read timeout of every call (default 5 seconds)
 */
@ConfigurationProperties(prefix = "app.messaging.provider")
public record ProviderProperties(String baseUrl, String authKey, String senderId, String otpSmsTemplateId,
		String whatsappOtpTemplate, Duration timeout) {

	public ProviderProperties {
		if (timeout == null) {
			timeout = Duration.ofSeconds(5);
		}
	}

}

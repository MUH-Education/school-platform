package com.muhjain.school.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.messaging.webhook.*}: how the provider's callbacks prove they come from the provider.
 *
 * @param secret shared with the provider (environment variable {@code APP_PROVIDER_WEBHOOK_SECRET}, never in the
 * repo). Every callback carries an HMAC-SHA256 of its raw body made with it. Empty = every callback is refused.
 * @param signatureHeader the header that carries it, hex, optionally after "sha256=". Example: {@code X-Signature}
 */
@ConfigurationProperties("app.messaging.webhook")
public record WebhookProperties(String secret, String signatureHeader) {

	public WebhookProperties {
		secret = (secret == null) ? "" : secret;
		signatureHeader = (signatureHeader == null || signatureHeader.isBlank()) ? "X-Signature" : signatureHeader.trim();
	}

	/** Never print the secret. */
	@Override
	public String toString() {
		return "WebhookProperties[signatureHeader=" + signatureHeader + ", secretSet=" + !secret.isEmpty() + "]";
	}

}

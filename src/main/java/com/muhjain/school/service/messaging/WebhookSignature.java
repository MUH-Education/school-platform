package com.muhjain.school.service.messaging;

import com.muhjain.school.config.WebhookProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Checks that a callback comes from the provider: its header carries the HMAC-SHA256 of the raw body, made with the
 * shared secret ({@code APP_PROVIDER_WEBHOOK_SECRET}), in hex, optionally after "sha256=". Compared in constant time.
 * No secret set → nothing is accepted. The secret and the signature are never logged.
 */
@Component
public class WebhookSignature {

	private final WebhookProperties properties;

	public WebhookSignature(WebhookProperties properties) {
		this.properties = properties;
	}

	public String headerName() {
		return properties.signatureHeader();
	}

	public boolean valid(byte[] body, String header) {
		if (properties.secret().isEmpty() || header == null || header.isBlank()) {
			return false;
		}
		String given = header.strip().toLowerCase(Locale.ROOT);
		if (given.startsWith("sha256=")) {
			given = given.substring("sha256=".length());
		}
		byte[] expected = sign(properties.secret(), body).getBytes(StandardCharsets.US_ASCII);
		return MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.US_ASCII));
	}

	/** The signature of a body, lower-case hex. Public so a test or a provider class can make one. */
	public static String sign(String secret, byte[] body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(body));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 is not available", ex);
		}
	}

}

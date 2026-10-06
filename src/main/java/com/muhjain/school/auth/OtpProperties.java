package com.muhjain.school.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.otp.*}. Example: 6 digits, 5 minutes, 5 wrong tries, 60 seconds between codes,
 * 5 codes per phone per hour, 20 per IP per hour, channels [whatsapp, sms].
 */
@ConfigurationProperties("app.otp")
public record OtpProperties(int length, Duration ttl, int maxAttempts, Duration resendAfter, int maxPerHour,
		int maxPerIpPerHour, List<String> channels, String hashSecret) {

	public static final int MIN_SECRET_BYTES = 32;

	public OtpProperties {
		if (hashSecret == null || hashSecret.isBlank()) {
			throw new IllegalStateException("app.otp.hash-secret is empty. Set the APP_OTP_SECRET environment variable.");
		}
		if (hashSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("app.otp.hash-secret is too short. APP_OTP_SECRET needs at least "
					+ MIN_SECRET_BYTES + " bytes.");
		}
		if (length < 4 || length > 9) {
			throw new IllegalStateException("app.otp.length must be between 4 and 9.");
		}
		if (channels == null || channels.isEmpty()) {
			throw new IllegalStateException("app.otp.channels is empty. Example: whatsapp,sms");
		}
		channels = channels.stream().map(String::strip).map(String::toUpperCase).toList();
	}

}

package com.muhjain.school.auth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.muhjain.school.common.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Tries the channels in the order of {@code app.otp.channels}. The first one that does not fail wins.
 * Example: channels whatsapp,sms → WhatsApp fails → SMS works → "SMS" is returned.
 * <p>
 * Checks at start-up: every channel in the list needs a sender, and {@code prod} refuses to start
 * with only the "log" channel (Phase 1 rule 10).
 */
@Service
public class OtpDeliveryService {

	private static final Logger log = LoggerFactory.getLogger(OtpDeliveryService.class);

	private final List<OtpSender> chain;

	public OtpDeliveryService(List<OtpSender> senders, OtpProperties properties, Environment environment) {
		Map<String, OtpSender> byChannel = new LinkedHashMap<>();
		senders.forEach(sender -> byChannel.put(sender.channel(), sender));
		List<OtpSender> ordered = new ArrayList<>();
		for (String channel : properties.channels()) {
			OtpSender sender = byChannel.get(channel);
			if (sender == null) {
				throw new IllegalStateException("app.otp.channels has '" + channel
						+ "', but there is no sender for it. Available: " + byChannel.keySet());
			}
			ordered.add(sender);
		}
		if (environment.matchesProfiles("prod") && properties.channels().equals(List.of("LOG"))) {
			throw new IllegalStateException(
					"In prod app.otp.channels cannot be only 'log'. Codes would never reach anybody.");
		}
		this.chain = List.copyOf(ordered);
	}

	/**
	 * @return the channel that sent the code, for example "WHATSAPP"
	 * @throws OtpSendException if every channel failed
	 */
	public String send(String phone, String code) throws OtpSendException {
		for (OtpSender sender : chain) {
			try {
				sender.send(phone, code);
				return sender.channel();
			}
			catch (OtpSendException | RuntimeException ex) {
				log.warn("OTP channel {} failed for {}: {}", sender.channel(), PhoneNumbers.mask(phone),
						ex.getMessage());
			}
		}
		throw new OtpSendException("Every OTP channel failed");
	}

}

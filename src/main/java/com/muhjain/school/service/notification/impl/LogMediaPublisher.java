package com.muhjain.school.service.notification.impl;

import com.muhjain.school.service.notification.MediaPublisher;
import com.muhjain.school.util.MediaRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Test mode: logs one line and answers a made-up id; nothing leaves the server. Used when
 * {@code app.messaging.whatsapp-provider} is {@code log} (local and test).
 * Example: {@code Media notifications/9b1e.mp4 (VIDEO) would be uploaded: log-3f2a...}.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.whatsapp-provider", havingValue = "log", matchIfMissing = true)
public class LogMediaPublisher implements MediaPublisher {

	private static final Logger log = LoggerFactory.getLogger(LogMediaPublisher.class);

	@Override
	public String publish(String mediaKey, MediaRules.Format format) {
		String id = "log-" + UUID.randomUUID();
		log.info("Media {} ({}) would be uploaded: {}", mediaKey, format.type(), id);
		return id;
	}

}

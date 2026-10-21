package com.muhjain.school.service.messaging;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.StatusWebhookRequest;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.MessageProblem;
import com.muhjain.school.model.repository.MessageOutboxRepository;
import com.muhjain.school.service.notification.NotificationDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * The provider's "delivered / read / failed" callbacks (rule 16 of Phase 11), for every outbox row (bus SMS too).
 * <ul>
 * <li>Found by {@code provider_ref}. An unknown id is ignored (answered 200, one log line): it may be a message of
 * another system on the same number.</li>
 * <li>Never backwards: "delivered" after "read" changes nothing, a failure after delivery changes nothing, and the
 * same callback twice changes nothing the second time.</li>
 * <li>A WhatsApp notification row that fails later with NOT_ON_WHATSAPP gets its SMS copy, once.</li>
 * </ul>
 */
@Service
public class DeliveryStatusService {

	private static final Logger log = LoggerFactory.getLogger(DeliveryStatusService.class);

	private final MessageOutboxRepository outbox;

	private final NotificationDelivery delivery;

	private final Clock clock;

	public DeliveryStatusService(MessageOutboxRepository outbox, NotificationDelivery delivery, Clock clock) {
		this.outbox = outbox;
		this.delivery = delivery;
		this.clock = clock;
	}

	/**
	 * @return true when a row changed
	 * @throws ApiException 400 VALIDATION (no providerRef or status)
	 */
	@Transactional
	public boolean apply(StatusWebhookRequest request) {
		if (request.providerRef() == null || request.providerRef().isBlank()) {
			throw ApiException.validation("providerRef", "is needed");
		}
		if (request.status() == null) {
			throw ApiException.validation("status", "must be DELIVERED, READ or FAILED");
		}
		Optional<MessageOutbox> found = outbox.findFirstByProviderRef(request.providerRef().strip());
		if (found.isEmpty()) {
			log.info("Status callback for an unknown message ignored");
			return false;
		}
		MessageOutbox row = found.get();
		Instant at = ((request.at() == null) ? Instant.now(clock) : request.at().toInstant())
			.truncatedTo(ChronoUnit.MICROS);
		boolean changed = switch (request.status()) {
			case DELIVERED -> row.markDelivered(at);
			case READ -> row.markRead(at);
			case FAILED -> failed(row, problemOf(request.problem()));
		};
		if (changed) {
			log.info("Message {} is now {}", row.getId(), request.status());
		}
		return changed;
	}

	private boolean failed(MessageOutbox row, MessageProblem problem) {
		if (!row.markProviderFailed(problem, "Provider: " + problem)) {
			return false;
		}
		if (problem == MessageProblem.NOT_ON_WHATSAPP && row.getChannel() == MessageChannel.WHATSAPP
				&& !outbox.existsByFallbackOfId(row.getId())) {
			delivery.smsFallback(row).ifPresent(outbox::save);
		}
		return true;
	}

	private static MessageProblem problemOf(String problem) {
		if (problem == null) {
			return MessageProblem.OTHER;
		}
		try {
			return MessageProblem.valueOf(problem.strip().toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			return MessageProblem.OTHER;
		}
	}

}

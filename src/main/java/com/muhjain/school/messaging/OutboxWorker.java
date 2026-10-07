package com.muhjain.school.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.muhjain.school.common.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the queued messages (rules 8 to 11 of the phase file). Real-life picture: the postman who empties the
 * school's letter box. {@link OutboxScheduler} calls {@link #runOnce()} every 5 seconds; a test calls it by hand.
 * <ul>
 * <li>Takes up to 50 QUEUED rows, oldest first, with {@code for update skip locked}. Two workers never send the
 * same row.</li>
 * <li>Sent: SENT with the provider id (or TEST_ONLY in test mode). Failed: {@code attempts + 1}, stays QUEUED;
 * after 3 failures FAILED.</li>
 * <li>A boarding SMS queued more than 2 hours ago is not sent: FAILED, "too old". A parent must not read "boarded at
 * 7:42" at noon.</li>
 * </ul>
 * One row that fails never stops the others.
 */
@Component
public class OutboxWorker {

	private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

	static final int BATCH_SIZE = 50;

	static final int MAX_ATTEMPTS = 3;

	static final Duration MAX_AGE = Duration.ofHours(2);

	static final String TOO_OLD = "too old";

	private final MessageOutboxRepository outbox;

	private final MessageTemplateRepository templates;

	private final SmsSender smsSender;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public OutboxWorker(MessageOutboxRepository outbox, MessageTemplateRepository templates, SmsSender smsSender,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.outbox = outbox;
		this.templates = templates;
		this.smsSender = smsSender;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** One round. Returns how many rows it handled (sent, failed or marked too old). */
	public int runOnce() {
		Integer handled = transaction.execute(status -> {
			List<MessageOutbox> batch = outbox.claimQueued(BATCH_SIZE);
			Instant now = Instant.now(clock);
			for (MessageOutbox message : batch) {
				handle(message, now);
			}
			return batch.size();
		});
		return (handled == null) ? 0 : handled;
	}

	private void handle(MessageOutbox message, Instant now) {
		if (message.getPurpose() == MessagePurpose.BOARDING
				&& message.getCreatedAt().plus(MAX_AGE).isBefore(now)) {
			message.markFailed(TOO_OLD);
			return;
		}
		try {
			String providerTemplateId = (message.getTemplateCode() == null) ? null
					: templates.findById(message.getTemplateCode()).map(MessageTemplate::getProviderTemplateId).orElse(null);
			SendResult result = smsSender.send(message.getPhone(), message.getBody(), providerTemplateId);
			message.markDone(result.testOnly() ? MessageStatus.TEST_ONLY : MessageStatus.SENT, result.providerRef(),
					now);
		}
		catch (SmsSendException | RuntimeException ex) {
			log.warn("SMS {} to {} failed (attempt {}): {}", message.getId(), PhoneNumbers.mask(message.getPhone()),
					message.getAttempts() + 1, ex.getMessage());
			message.markAttemptFailed(ex.getMessage(), MAX_ATTEMPTS);
		}
	}

}

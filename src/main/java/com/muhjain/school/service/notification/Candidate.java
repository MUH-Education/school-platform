package com.muhjain.school.service.notification;

import com.muhjain.school.model.dto.response.Recipient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One recipient while it passes the guard chain. The guards may add values (the fee numbers read now) and hold the
 * message until a later time (quiet hours).
 * Example: Punia family, values {"Amount due": "₹8,500"}, hold until 8:00 tomorrow.
 */
public final class Candidate {

	private final Recipient recipient;

	private final Map<String, String> values;

	private final Instant queuedAt;

	private Instant holdUntil;

	/**
	 * @param queuedAt when the outbox row was written; null at expand time (there is no row yet)
	 */
	public Candidate(Recipient recipient, Instant queuedAt) {
		this.recipient = recipient;
		this.values = new LinkedHashMap<>(recipient.values());
		this.queuedAt = queuedAt;
	}

	public Recipient recipient() {
		return recipient;
	}

	/** The values as they are now, with what the guards added. */
	public Map<String, String> values() {
		return Map.copyOf(values);
	}

	public void putValues(Map<String, String> more) {
		values.putAll(more);
	}

	public Instant queuedAt() {
		return queuedAt;
	}

	public Instant holdUntil() {
		return holdUntil;
	}

	/** Do not send before this moment. The latest hold wins. */
	public void holdUntil(Instant moment) {
		if (holdUntil == null || moment.isAfter(holdUntil)) {
			holdUntil = moment;
		}
	}

}

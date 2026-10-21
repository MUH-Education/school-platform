package com.muhjain.school.service.notification;

import com.muhjain.school.model.entity.MessageTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What every guard of one run shares: the moment, the school day, the notification's template and policy, and a
 * small cache so a list that all recipients need (the stop list) is read once per run, not once per person.
 * Made fresh for every run: nothing lives longer than one expand or one send.
 */
public final class GuardContext {

	private final Instant now;

	private final LocalDate today;

	private final MessageTemplate template;

	private final KindPolicy policy;

	private final Map<String, Object> cache = new HashMap<>();

	private final Set<String> phonesSeen = new HashSet<>();

	public GuardContext(Instant now, LocalDate today, MessageTemplate template, KindPolicy policy) {
		this.now = now;
		this.today = today;
		this.template = template;
		this.policy = policy;
	}

	public Instant now() {
		return now;
	}

	public LocalDate today() {
		return today;
	}

	public MessageTemplate template() {
		return template;
	}

	public KindPolicy policy() {
		return policy;
	}

	/** Loads once per run. Example: {@code context.cached("stoppedPhones", stopList::stoppedPhones)}. */
	@SuppressWarnings("unchecked")
	public <T> T cached(String key, Supplier<T> loader) {
		return (T) cache.computeIfAbsent(key, k -> loader.get());
	}

	/** True the first time this phone is seen in this run, false after. */
	public boolean firstTime(String phone) {
		return phonesSeen.add(phone);
	}

}

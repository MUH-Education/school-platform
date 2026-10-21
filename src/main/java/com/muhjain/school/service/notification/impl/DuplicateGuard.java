package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.service.notification.Candidate;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import org.springframework.stereotype.Component;

import com.muhjain.school.model.repository.MessageOutboxRepository;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 11, every kind: the same phone already has a row of this notification, or came earlier in this run → DUPLICATE.
 * The unique index (notification_id, phone, channel) backs it up.
 */
@Component
public class DuplicateGuard implements RecipientGuard {

	private final MessageOutboxRepository outbox;

	public DuplicateGuard(MessageOutboxRepository outbox) {
		this.outbox = outbox;
	}

	@Override
	public int order() {
		return 11;
	}

	@Override
	public boolean appliesTo(NotificationKind kind) {
		return true;
	}

	@Override
	public Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context) {
		Set<String> existing = context.cached("phonesOfNotification",
				() -> (notification.getId() == null) ? Set.<String>of()
						: new HashSet<>(outbox.phonesOfNotification(notification.getId())));
		String phone = candidate.recipient().phone();
		return (existing.contains(phone) || !context.firstTime(phone)) ? Optional.of(SkipReason.DUPLICATE)
				: Optional.empty();
	}

}

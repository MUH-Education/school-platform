package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.service.notification.Candidate;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import org.springframework.stereotype.Component;

import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.enums.MessageStatus;
import com.muhjain.school.model.repository.MessageOutboxRepository;
import com.muhjain.school.service.setting.SettingService;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 14, MARKETING and GREETING: this phone already has {@code notifications.max_marketing_per_family_per_week} (2)
 * marketing or greeting messages from the last 7 days → WEEKLY_CAP. A greeting is sent as MARKETING, so both count.
 * Example: open day on Monday, admissions on Thursday, a third on Saturday → the third is skipped.
 */
@Component
public class WeeklyCapGuard implements RecipientGuard {

	/** The rows that count: they went, or will go. */
	public static final Set<MessageStatus> COUNTED = EnumSet.of(MessageStatus.QUEUED, MessageStatus.SENT,
			MessageStatus.DELIVERED, MessageStatus.TEST_ONLY);

	private final MessageOutboxRepository outbox;

	private final SettingService settings;

	public WeeklyCapGuard(MessageOutboxRepository outbox, SettingService settings) {
		this.outbox = outbox;
		this.settings = settings;
	}

	@Override
	public int order() {
		return 14;
	}

	@Override
	public boolean appliesTo(NotificationKind kind) {
		return kind == NotificationKind.MARKETING || kind == NotificationKind.GREETING;
	}

	@Override
	public Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context) {
		int cap = context.cached("weeklyCap",
				() -> Integer.parseInt(settings.value("notifications.max_marketing_per_family_per_week")));
		long sent = outbox.countByPhoneAndPurposeAndStatusInAndCreatedAtGreaterThanEqualAndNotificationIdNot(
				candidate.recipient().phone(), MessagePurpose.MARKETING, COUNTED, context.now().minus(Duration.ofDays(7)),
				(notification.getId() == null) ? -1L : notification.getId());
		return (sent >= cap) ? Optional.of(SkipReason.WEEKLY_CAP) : Optional.empty();
	}

}

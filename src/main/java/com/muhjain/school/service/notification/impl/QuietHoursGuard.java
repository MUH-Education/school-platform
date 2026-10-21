package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.service.notification.Candidate;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import org.springframework.stereotype.Component;

import com.muhjain.school.service.setting.SettingService;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 20, every kind except URGENT (its policy ignores quiet hours): never a skip. Inside quiet hours
 * ({@code notifications.quiet_from} to {@code quiet_to}, school time, default 21:00 to 08:00) the message is held
 * until the quiet hours end. Example: a greeting expanded at 22:15 waits until 08:00 the next morning.
 */
@Component
public class QuietHoursGuard implements RecipientGuard {

	private final SettingService settings;

	private final Clock clock;

	public QuietHoursGuard(SettingService settings, Clock clock) {
		this.settings = settings;
		this.clock = clock;
	}

	@Override
	public int order() {
		return 20;
	}

	@Override
	public boolean appliesTo(NotificationKind kind) {
		return kind != NotificationKind.URGENT;
	}

	@Override
	public Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context) {
		if (context.policy().ignoresQuietHours()) {
			return Optional.empty();
		}
		LocalTime from = context.cached("quietFrom", () -> LocalTime.parse(settings.value("notifications.quiet_from")));
		LocalTime to = context.cached("quietTo", () -> LocalTime.parse(settings.value("notifications.quiet_to")));
		ZonedDateTime now = context.now().atZone(clock.getZone());
		quietEnd(now, from, to).ifPresent(end -> candidate.holdUntil(end.toInstant()));
		return Optional.empty();
	}

	/**
	 * When the quiet hours that hold {@code now} end, or empty when {@code now} is not inside them. Works for hours
	 * over midnight (21:00 to 08:00) and inside one day (13:00 to 14:00). Equal times = no quiet hours.
	 */
	public static Optional<ZonedDateTime> quietEnd(ZonedDateTime now, LocalTime from, LocalTime to) {
		LocalTime t = now.toLocalTime();
		if (from.equals(to)) {
			return Optional.empty();
		}
		boolean overMidnight = from.isAfter(to);
		boolean inside = overMidnight ? (!t.isBefore(from) || t.isBefore(to)) : (!t.isBefore(from) && t.isBefore(to));
		if (!inside) {
			return Optional.empty();
		}
		ZonedDateTime end = now.with(to).withSecond(0).withNano(0);
		return Optional.of(end.isAfter(now) ? end : end.plusDays(1));
	}

}

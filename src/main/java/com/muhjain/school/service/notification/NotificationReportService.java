package com.muhjain.school.service.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.exception.MediaPublishException;
import com.muhjain.school.model.dto.response.NotificationReportResponse;
import com.muhjain.school.model.dto.response.ReadsInHour;
import com.muhjain.school.model.dto.response.ResendFailedResponse;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.AuditAction;
import com.muhjain.school.model.enums.ButtonAction;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.MessageProblem;
import com.muhjain.school.model.enums.MessageStatus;
import com.muhjain.school.model.enums.NotificationStatus;
import com.muhjain.school.model.enums.Permission;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.model.repository.MessageOutboxRepository;
import com.muhjain.school.model.repository.NotificationRepository;
import com.muhjain.school.service.audit.AuditService;
import com.muhjain.school.service.notification.impl.QuietHoursGuard;
import com.muhjain.school.service.setting.SettingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The report of a notification and "resend failed" (docs/10-notifications.md section 8, rule 14).
 * The report reads only the notification's outbox rows with {@code group by}: there is no report table, so it is
 * always right. Example: "Fee reminder · 41 · 33 got it · 8 skipped: 5 already paid, 2 promised, 1 reminded".
 */
@Service
public class NotificationReportService {

	static final String ENTITY = "NOTIFICATION";

	private static final Set<NotificationStatus> RESENDABLE = EnumSet.of(NotificationStatus.SENDING,
			NotificationStatus.SENT, NotificationStatus.FAILED);

	private final NotificationRepository notifications;

	private final MessageOutboxRepository outbox;

	private final KindPolicies policies;

	private final NotificationMediaService mediaService;

	private final SettingService settings;

	private final AuditService auditService;

	private final Clock clock;

	public NotificationReportService(NotificationRepository notifications, MessageOutboxRepository outbox,
			KindPolicies policies, NotificationMediaService mediaService, SettingService settings,
			AuditService auditService, Clock clock) {
		this.notifications = notifications;
		this.outbox = outbox;
		this.policies = policies;
		this.mediaService = mediaService;
		this.settings = settings;
		this.auditService = auditService;
		this.clock = clock;
	}

	/** @throws ApiException 404 NOT_FOUND */
	@Transactional(readOnly = true)
	public NotificationReportResponse report(Long id) {
		Notification n = notifications.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This notification does not exist."));
		Map<MessageStatus, Long> byStatus = counts(outbox.countByStatus(id), MessageStatus.class);
		Map<MessageProblem, Long> byProblem = new EnumMap<>(MessageProblem.class);
		for (Object[] pair : outbox.countFailedByProblem(id)) {
			MessageProblem problem = (pair[0] == null) ? MessageProblem.OTHER : (MessageProblem) pair[0];
			byProblem.merge(problem, ((Number) pair[1]).longValue(), Long::sum);
		}
		Object[] totals = outbox.countDeliveredReadCopies(id).getFirst();
		List<ReadsInHour> readByHour = outbox.readsByHour(id, clock.getZone().getId())
			.stream()
			.map(pair -> new ReadsInHour(atSchoolHour(pair[0]), ((Number) pair[1]).longValue()))
			.toList();
		long rows = byStatus.values().stream().mapToLong(Long::longValue).sum();
		long gotIt = get(byStatus, MessageStatus.SENT) + get(byStatus, MessageStatus.DELIVERED)
				+ get(byStatus, MessageStatus.TEST_ONLY);
		return new NotificationReportResponse(n.getId(), n.getKind(), n.getName(), n.getStatus(), n.getRecipientCount(),
				at(n.getExpandedAt()), at(n.getFinishedAt()), rows, gotIt, number(totals[0]), number(totals[1]),
				get(byStatus, MessageStatus.SKIPPED), get(byStatus, MessageStatus.FAILED),
				get(byStatus, MessageStatus.QUEUED), get(byStatus, MessageStatus.CANCELLED), byStatus,
				counts(outbox.countByChannel(id), MessageChannel.class),
				counts(outbox.countBySkipReason(id), SkipReason.class), byProblem,
				counts(outbox.countByButton(id), ButtonAction.class), readByHour, number(totals[2]), number(totals[3]));
	}

	/**
	 * Puts the failed messages that are worth a second try back in the queue: the same rows, fresh tries (not a
	 * wrong number, not "no WhatsApp" — that one got its SMS copy — not DND). The worker's checks run again, so a
	 * family that paid in the meantime is skipped, and an URGENT message older than 2 hours is not sent. The file is
	 * uploaded to the provider again if its id is older than 25 days. The notification is SENDING again until the
	 * rows are done. Inside quiet hours the rows wait for the morning (not URGENT).
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 NOTIFICATION_NOT_EDITABLE (not sent yet, or cancelled),
	 * 403 KIND_NOT_ALLOWED_FOR_ROLE, 503 MEDIA_UPLOAD_FAILED
	 */
	@Transactional
	public ResendFailedResponse resendFailed(Long id, Set<Permission> permissions, Long userId) {
		Notification n = notifications.findLockedById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This notification does not exist."));
		if (!RESENDABLE.contains(n.getStatus())) {
			throw new ApiException(HttpStatus.CONFLICT, "NOTIFICATION_NOT_EDITABLE",
					"Only a notification that was sent can be sent again (this one is "
							+ n.getStatus().name().toLowerCase() + ").");
		}
		policies.checkMaySend(n.getKind(), permissions);
		List<MessageOutbox> failed = outbox.failedWorthRetrying(id);
		if (failed.isEmpty()) {
			return new ResendFailedResponse(0);
		}
		try {
			mediaService.ensurePublished(n);
		}
		catch (MediaPublishException ex) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_UPLOAD_FAILED",
					"The WhatsApp provider did not take the file. Try again in a minute.");
		}
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		Instant from = startOf(now, policies.forKind(n.getKind()));
		failed.forEach(row -> row.requeue(from));
		outbox.saveAll(failed);
		n.setStatus(NotificationStatus.SENDING);
		n.setFinishedAt(null);
		n.setNote("Resent " + failed.size() + " failed message" + ((failed.size() == 1) ? "" : "s") + ".");
		notifications.save(n);
		auditService.record(ENTITY, id, AuditAction.UPDATED, "Notification \"" + n.getName() + "\": " + n.getNote(),
				Map.of("requeued", failed.size()));
		return new ResendFailedResponse(failed.size());
	}

	// Now, or the end of the quiet hours (URGENT ignores them).
	private Instant startOf(Instant now, KindPolicy policy) {
		if (policy.ignoresQuietHours()) {
			return now;
		}
		LocalTime from = LocalTime.parse(settings.value("notifications.quiet_from"));
		LocalTime to = LocalTime.parse(settings.value("notifications.quiet_to"));
		return QuietHoursGuard.quietEnd(now.atZone(clock.getZone()), from, to).map(z -> z.toInstant()).orElse(now);
	}

	private static <E extends Enum<E>> Map<E, Long> counts(List<Object[]> pairs, Class<E> type) {
		Map<E, Long> map = new EnumMap<>(type);
		for (Object[] pair : pairs) {
			if (pair[0] != null) {
				map.put(type.cast(pair[0]), ((Number) pair[1]).longValue());
			}
		}
		return map;
	}

	private static long get(Map<MessageStatus, Long> map, MessageStatus status) {
		return map.getOrDefault(status, 0L);
	}

	private static long number(Object value) {
		return (value == null) ? 0 : ((Number) value).longValue();
	}

	// The database gives the hour in school time without a zone; give it its offset back.
	private OffsetDateTime atSchoolHour(Object value) {
		LocalDateTime hour = (value instanceof Timestamp t) ? t.toLocalDateTime() : (LocalDateTime) value;
		return hour.atZone(clock.getZone()).toOffsetDateTime();
	}

	private OffsetDateTime at(Instant instant) {
		return (instant == null) ? null : instant.atZone(clock.getZone()).toOffsetDateTime();
	}

}

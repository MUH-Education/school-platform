package com.muhjain.school.service.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.NotificationRequest;
import com.muhjain.school.model.dto.request.SendNotificationRequest;
import com.muhjain.school.model.dto.response.AudienceCounts;
import com.muhjain.school.model.dto.response.NotificationRecipientResponse;
import com.muhjain.school.model.dto.response.NotificationResponse;
import com.muhjain.school.model.dto.response.PageResponse;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.TestSendResponse;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.entity.MessageTemplate;
import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.AuditAction;
import com.muhjain.school.model.enums.HeaderType;
import com.muhjain.school.model.enums.MediaFileType;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.enums.MessageStatus;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.NotificationStatus;
import com.muhjain.school.model.enums.Permission;
import com.muhjain.school.model.enums.RecipientType;
import com.muhjain.school.model.enums.TemplateApproval;
import com.muhjain.school.model.enums.TemplateKind;
import com.muhjain.school.model.repository.MessageOutboxRepository;
import com.muhjain.school.model.repository.MessageTemplateRepository;
import com.muhjain.school.model.repository.NotificationRepository;
import com.muhjain.school.service.audit.AuditService;
import com.muhjain.school.service.notification.impl.QuietHoursGuard;
import com.muhjain.school.service.setting.SettingService;
import com.muhjain.school.service.user.UserService;
import com.muhjain.school.util.MediaRules;
import com.muhjain.school.util.PhoneNumbers;
import com.muhjain.school.util.Placeholders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The office's notifications (docs/10-notifications.md section 1, rules 1 to 13 of the phase file).
 * <ul>
 * <li>A notification is made as a DRAFT and answers the counts of "Who gets it", worked out now.</li>
 * <li>"Send now" only sets SENDING and "later" only sets SCHEDULED: the outbox rows are written by
 * {@code NotificationDispatcher}, never here, so a request is always quick.</li>
 * <li>Every change checks the status it expects first: 409 NOTIFICATION_NOT_EDITABLE otherwise.</li>
 * <li>The kind decides who may send (403 KIND_NOT_ALLOWED_FOR_ROLE) and which audiences are allowed.</li>
 * </ul>
 */
@Service
public class NotificationService {

	static final String ENTITY = "NOTIFICATION";

	private static final Set<NotificationStatus> EDITABLE = EnumSet.of(NotificationStatus.DRAFT,
			NotificationStatus.SCHEDULED);

	/** Rows of a notification that went (or went to the log in test mode). */
	private static final Set<MessageStatus> GONE = EnumSet.of(MessageStatus.SENT, MessageStatus.DELIVERED,
			MessageStatus.TEST_ONLY);

	private final NotificationRepository notifications;

	private final MessageTemplateRepository templates;

	private final MessageOutboxRepository outbox;

	private final KindPolicies policies;

	private final AudienceService audienceService;

	private final NotificationMediaService mediaService;

	private final SettingService settings;

	private final UserService userService;

	private final AuditService auditService;

	private final Clock clock;

	public NotificationService(NotificationRepository notifications, MessageTemplateRepository templates,
			MessageOutboxRepository outbox, KindPolicies policies, AudienceService audienceService,
			NotificationMediaService mediaService, SettingService settings, UserService userService,
			AuditService auditService, Clock clock) {
		this.notifications = notifications;
		this.templates = templates;
		this.outbox = outbox;
		this.policies = policies;
		this.audienceService = audienceService;
		this.mediaService = mediaService;
		this.settings = settings;
		this.userService = userService;
		this.auditService = auditService;
		this.clock = clock;
	}

	// ---------------------------------------------------------------- make and change

	/**
	 * A new DRAFT. Answers the counts of who would get it now.
	 *
	 * @param permissions the logged-in user's permissions (from the role)
	 * @throws ApiException 403 KIND_NOT_ALLOWED_FOR_ROLE, 409 AUDIENCE_NOT_ALLOWED, 409 TEMPLATE_NOT_FOR_KIND,
	 * 400 VALIDATION
	 */
	@Transactional
	public NotificationResponse create(NotificationRequest request, Set<Permission> permissions, Long userId) {
		Notification n = new Notification();
		n.setCreatedBy(userId);
		prepare(n, request, permissions);
		notifications.save(n);
		auditService.record(ENTITY, n.getId(), AuditAction.CREATED,
				"Notification \"" + n.getName() + "\" made (" + n.getKind() + "), draft.", Map.of("kind",
						n.getKind().name()));
		return toResponse(n, true);
	}

	/**
	 * Changes a DRAFT or SCHEDULED notification (a scheduled one stays scheduled).
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 NOTIFICATION_NOT_EDITABLE, and the checks of {@link #create}
	 */
	@Transactional
	public NotificationResponse update(Long id, NotificationRequest request, Set<Permission> permissions,
			Long userId) {
		Notification n = find(id);
		requireStatus(n, EDITABLE);
		policies.checkMaySend(n.getKind(), permissions);
		prepare(n, request, permissions);
		notifications.saveAndFlush(n);
		auditService.record(ENTITY, id, AuditAction.UPDATED, "Notification \"" + n.getName() + "\" changed.",
				Map.of());
		return toResponse(n, true);
	}

	// Every check of a draft, then the fields. Used by create and update.
	private void prepare(Notification n, NotificationRequest request, Set<Permission> permissions) {
		NotificationKind kind = request.kind();
		policies.checkMaySend(kind, permissions);
		policies.checkAudience(kind, request.audience());
		MessageTemplate template = templates.findById(request.templateId())
			.filter(t -> t.getKind() == TemplateKind.of(kind) && t.isActive())
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "TEMPLATE_NOT_FOR_KIND",
					"Pick a " + kind.name().toLowerCase().replace('_', ' ') + " template that is in use."));

		String body = bodyOf(request, template, policies.forKind(kind));
		String sms = (request.smsBody() == null || request.smsBody().isBlank()) ? template.getSmsBody()
				: request.smsBody().strip();
		Map<String, String> fixed = fixedValues(request.fixedValues(), body, sms);

		n.setKind(kind);
		n.setName(request.name().strip());
		n.setTemplateId(template.getId());
		n.setBody(body);
		n.setSmsBody(sms);
		n.setFixedValues(fixed);
		setMedia(n, request.mediaKey(), template);
		n.setAudience(request.audience());
	}

	// An approved template's text is what WhatsApp sends, so it cannot be changed here. Only ONE_FAMILY and STAFF
	// with a template that is not approved may send their own text (inside the 24-hour window).
	private static String bodyOf(NotificationRequest request, MessageTemplate template, KindPolicy policy) {
		String typed = (request.body() == null || request.body().isBlank()) ? null : request.body().strip();
		boolean freeText = policy.freeTextInsideWindow() && template.getApproval() != TemplateApproval.APPROVED;
		if (freeText) {
			return (typed != null) ? typed : template.getBody();
		}
		String templateText = template.textToSend();
		if (typed != null && !typed.equals(templateText)) {
			throw ApiException.validation("body", "comes from the template; change the template instead");
		}
		return templateText;
	}

	// Every {box} the recipients do not fill needs a value; values for boxes that are not in the text are dropped.
	private static Map<String, String> fixedValues(Map<String, String> given, String body, String sms) {
		Set<String> boxes = new LinkedHashSet<>(Placeholders.of(body));
		boxes.addAll(Placeholders.of(sms));
		Map<String, String> asked = (given == null) ? Map.of() : given;
		Map<String, String> result = new LinkedHashMap<>();
		for (String box : boxes) {
			String value = asked.get(box);
			if (value != null && !value.isBlank()) {
				result.put(box, value.strip());
			}
			else if (!RecipientBoxes.STANDARD.contains(box)) {
				throw ApiException.validation("fixedValues", "needs a value for {" + box + "}");
			}
		}
		return result;
	}

	// The template's file, or another file of the same type; a template with no header cannot carry a file.
	private void setMedia(Notification n, String mediaKey, MessageTemplate template) {
		HeaderType header = template.getHeaderType();
		if (mediaKey != null && !mediaKey.isBlank()) {
			if (header == HeaderType.NONE) {
				throw ApiException.validation("mediaKey", "this template has no picture, video or file");
			}
			MediaRules.Format format = mediaService.requireFile(mediaKey, MediaFileType.valueOf(header.name()),
					"mediaKey");
			n.setMediaKey(mediaKey);
			n.setMediaType(format.type());
		}
		else if (header != HeaderType.NONE) {
			n.setMediaKey(template.getMediaKey());
			n.setMediaType(MediaFileType.valueOf(header.name()));
		}
		else {
			n.setMediaKey(null);
			n.setMediaType(null);
		}
		// A new file needs a new upload to the provider.
		n.setProviderMediaId(null);
		n.setProviderMediaAt(null);
	}

	// ---------------------------------------------------------------- send

	/**
	 * Send now (status SENDING) or later (SCHEDULED). Nothing is written to the outbox here: the dispatcher does it
	 * within a minute. Example: the owner clicks Send at 4:15:10 pm; this answers at once; the rows exist by 4:16.
	 *
	 * @throws ApiException 409 NOTIFICATION_NOT_EDITABLE, 403 KIND_NOT_ALLOWED_FOR_ROLE, 409 TEMPLATE_NOT_APPROVED,
	 * 409 NO_RECIPIENTS, 409 SCHEDULE_IN_PAST, 409 SCHEDULE_IN_QUIET_HOURS, 400 VALIDATION (both or neither of
	 * sendNow and scheduledAt)
	 */
	@Transactional
	public NotificationResponse send(Long id, SendNotificationRequest request, Set<Permission> permissions,
			Long userId) {
		Notification n = find(id);
		requireStatus(n, EDITABLE);
		policies.checkMaySend(n.getKind(), permissions);
		policies.checkAudience(n.getKind(), n.getAudience());
		KindPolicy policy = policies.forKind(n.getKind());
		boolean now = Boolean.TRUE.equals(request.sendNow());
		if (now == (request.scheduledAt() != null)) {
			throw ApiException.validation("sendNow", "send now, or give scheduledAt, not both");
		}
		MessageTemplate template = templates.findById(n.getTemplateId()).orElseThrow();
		if (template.getApproval() != TemplateApproval.APPROVED && !policy.freeTextInsideWindow()) {
			throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_NOT_APPROVED",
					"WhatsApp has not approved this template yet.");
		}
		AudienceCounts counts = audienceService.resolve(n.getKind(), n.getAudience()).counts();
		if (counts.total() == 0) {
			throw new ApiException(HttpStatus.CONFLICT, "NO_RECIPIENTS", "Nobody would get this message.");
		}
		String summary;
		if (now) {
			n.setStatus(NotificationStatus.SENDING);
			n.setScheduledAt(null);
			summary = "Notification \"" + n.getName() + "\" sent to " + counts.total() + " people.";
		}
		else {
			Instant at = request.scheduledAt().toInstant();
			checkSchedule(at, policy);
			n.setStatus(NotificationStatus.SCHEDULED);
			n.setScheduledAt(at);
			summary = "Notification \"" + n.getName() + "\" scheduled for " + at.atZone(clock.getZone())
				.toOffsetDateTime() + ".";
		}
		notifications.saveAndFlush(n);
		auditService.record(ENTITY, id, AuditAction.UPDATED, summary, Map.of("status", n.getStatus().name()));
		return toResponse(n, false);
	}

	private void checkSchedule(Instant at, KindPolicy policy) {
		if (!at.isAfter(Instant.now(clock))) {
			throw new ApiException(HttpStatus.CONFLICT, "SCHEDULE_IN_PAST", "Pick a time that has not passed.");
		}
		if (policy.ignoresQuietHours()) {
			return;
		}
		LocalTime from = LocalTime.parse(settings.value("notifications.quiet_from"));
		LocalTime to = LocalTime.parse(settings.value("notifications.quiet_to"));
		if (QuietHoursGuard.quietEnd(at.atZone(clock.getZone()), from, to).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "SCHEDULE_IN_QUIET_HOURS",
					"Between " + from + " and " + to + " nothing is sent. Pick " + to + " or later.");
		}
	}

	/**
	 * One copy to the caller's own phone, with the template's example values (rule 12). Purpose TEST: not counted,
	 * not checked by the guards, and it does not stop the real message to the same phone.
	 *
	 * @throws ApiException 409 NOTIFICATION_NOT_EDITABLE
	 */
	@Transactional
	public TestSendResponse test(Long id, Set<Permission> permissions, Long userId) {
		Notification n = find(id);
		requireStatus(n, EDITABLE);
		policies.checkMaySend(n.getKind(), permissions);
		AppUser user = userService.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such user."));
		MessageTemplate template = templates.findById(n.getTemplateId()).orElseThrow();
		Map<String, String> values = new LinkedHashMap<>(template.getExampleValues());
		values.putAll(n.getFixedValues());
		String text = cut(Placeholders.fill(n.getBody(), values));
		MessageOutbox row = new MessageOutbox(MessagePurpose.TEST, MessageChannel.WHATSAPP, user.getPhone(), text,
				Instant.now(clock));
		row.setNotificationId(id);
		row.setCreatedBy(userId);
		row.setMediaKey(n.getMediaKey());
		row.setTemplateValues(values);
		outbox.save(row);
		return new TestSendResponse(PhoneNumbers.mask(user.getPhone()), text);
	}

	/**
	 * Cancel (rule 13). DRAFT or SCHEDULED: nothing is ever sent. SENDING: the rows not sent yet become CANCELLED,
	 * the sent ones stay, and the note says how many of each. Waits for a dispatcher that is expanding it.
	 *
	 * @throws ApiException 409 NOTIFICATION_NOT_EDITABLE (SENT, CANCELLED, FAILED)
	 */
	@Transactional
	public NotificationResponse cancel(Long id, Set<Permission> permissions, Long userId) {
		Notification n = notifications.findLockedById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This notification does not exist."));
		requireStatus(n, EnumSet.of(NotificationStatus.DRAFT, NotificationStatus.SCHEDULED, NotificationStatus.SENDING));
		policies.checkMaySend(n.getKind(), permissions);
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		if (n.getStatus() == NotificationStatus.SENDING) {
			int stopped = outbox.cancelQueued(id, now);
			long sent = outbox.countOfNotification(id, GONE);
			n.setNote("Cancelled while sending: " + sent + " sent, " + stopped + " not sent.");
			n.setFinishedAt(now);
		}
		else {
			n.setNote("Cancelled before sending.");
		}
		n.setStatus(NotificationStatus.CANCELLED);
		notifications.saveAndFlush(n);
		auditService.record(ENTITY, id, AuditAction.CANCELLED, "Notification \"" + n.getName() + "\": " + n.getNote(),
				Map.of());
		return toResponse(n, false);
	}

	// ---------------------------------------------------------------- read

	/** One notification; a DRAFT or SCHEDULED one with the counts of now. @throws ApiException 404 NOT_FOUND */
	@Transactional(readOnly = true)
	public NotificationResponse get(Long id) {
		Notification n = find(id);
		return toResponse(n, EDITABLE.contains(n.getStatus()));
	}

	/**
	 * The list of the Home and Reports screens, newest first. Every filter is optional; {@code from}/{@code to} are
	 * school days of {@code created_at}, both included.
	 */
	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> list(NotificationKind kind, NotificationStatus status, LocalDate from,
			LocalDate to, int page, int size) {
		Instant start = (from == null) ? Instant.EPOCH : from.atStartOfDay(clock.getZone()).toInstant();
		Instant end = ((to == null) ? LocalDate.now(clock).plusYears(1) : to.plusDays(1)).atStartOfDay(clock.getZone())
			.toInstant();
		Page<Notification> found = notifications.search(kind, status, start, end, pageRequest(page, size));
		Map<Long, String> templateNames = new java.util.HashMap<>();
		templates.findAllById(found.getContent().stream().map(Notification::getTemplateId).distinct().toList())
			.forEach(t -> templateNames.put(t.getId(), t.getName()));
		Map<Long, String> userNames = userService
			.displayNames(found.getContent().stream().map(Notification::getCreatedBy).distinct().toList());
		return PageResponse.of(found.map(n -> toResponse(n, null, templateNames.get(n.getTemplateId()),
				userNames.get(n.getCreatedBy()))));
	}

	/**
	 * "See the 246 families". Before sending: the people who would get it now. After: the outbox rows, with status
	 * and skip reason. Phones are masked.
	 */
	@Transactional(readOnly = true)
	public PageResponse<NotificationRecipientResponse> recipients(Long id, int page, int size) {
		Notification n = find(id);
		PageRequest request = pageRequest(page, size);
		if (EDITABLE.contains(n.getStatus())) {
			List<Recipient> all = audienceService.resolve(n.getKind(), n.getAudience()).recipients();
			int from = (int) Math.min(request.getOffset(), all.size());
			int to = Math.min(from + request.getPageSize(), all.size());
			List<NotificationRecipientResponse> items = all.subList(from, to)
				.stream()
				.map(r -> new NotificationRecipientResponse(r.type(), r.personId(), r.values().get("Name"),
						r.values().get("Child name"), PhoneNumbers.mask(r.phone()), r.channel(), r.group(), null, null))
				.toList();
			int pages = (int) Math.ceil(all.size() / (double) request.getPageSize());
			return new PageResponse<>(items, request.getPageNumber(), request.getPageSize(), all.size(), pages);
		}
		return PageResponse.of(outbox.pageOfNotification(id, request).map(NotificationService::rowLine));
	}

	private static NotificationRecipientResponse rowLine(MessageOutbox row) {
		RecipientType type = (row.getGuardianId() != null)
				? RecipientType.GUARDIAN
				: (row.getStaffId() != null) ? RecipientType.STAFF
						: RecipientType.ENQUIRY;
		Long personId = (row.getGuardianId() != null) ? row.getGuardianId()
				: (row.getStaffId() != null) ? row.getStaffId() : row.getEnquiryId();
		return new NotificationRecipientResponse(type, personId, row.getTemplateValues().get("Name"),
				row.getTemplateValues().get("Child name"), PhoneNumbers.mask(row.getPhone()), row.getChannel(), null,
				row.getStatus(), row.getSkipReason());
	}

	// ---------------------------------------------------------------- helpers

	private Notification find(Long id) {
		return notifications.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This notification does not exist."));
	}

	private static void requireStatus(Notification n, Set<NotificationStatus> allowed) {
		if (!allowed.contains(n.getStatus())) {
			throw new ApiException(HttpStatus.CONFLICT, "NOTIFICATION_NOT_EDITABLE",
					"This notification is " + n.getStatus().name().toLowerCase() + " and cannot be changed now.");
		}
	}

	private static PageRequest pageRequest(int page, int size) {
		if (page < 0) {
			throw ApiException.validation("page", "must be 0 or more");
		}
		if (size < 1 || size > 100) {
			throw ApiException.validation("size", "must be 1 to 100");
		}
		return PageRequest.of(page, size);
	}

	// The outbox body holds at most 1024 letters (WhatsApp's limit).
	private static String cut(String text) {
		return (text.length() <= 1024) ? text : text.substring(0, 1024);
	}

	private NotificationResponse toResponse(Notification n, boolean withCounts) {
		String templateName = templates.findById(n.getTemplateId()).map(MessageTemplate::getName).orElse(null);
		String userName = userService.displayNames(List.of(n.getCreatedBy())).get(n.getCreatedBy());
		AudienceCounts counts = withCounts ? audienceService.resolve(n.getKind(), n.getAudience()).counts() : null;
		return toResponse(n, counts, templateName, userName);
	}

	private NotificationResponse toResponse(Notification n, AudienceCounts counts, String templateName,
			String userName) {
		return new NotificationResponse(n.getId(), n.getKind(), n.getName(), n.getTemplateId(), templateName,
				n.getBody(), n.getSmsBody(), n.getFixedValues(), n.getMediaKey(), n.getMediaType(), n.getAudience(),
				n.getStatus(), at(n.getScheduledAt()), at(n.getExpandedAt()), at(n.getFinishedAt()),
				n.getRecipientCount(), n.getNote(), n.getSource(), n.getCreatedBy(), userName, at(n.getCreatedAt()),
				counts);
	}

	private OffsetDateTime at(Instant instant) {
		return (instant == null) ? null : instant.atZone(clock.getZone()).toOffsetDateTime();
	}

}

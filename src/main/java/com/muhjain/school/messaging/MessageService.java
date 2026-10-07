package com.muhjain.school.messaging;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.PageResponse;
import com.muhjain.school.common.PhoneNumbers;
import com.muhjain.school.student.StudentQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Messages screen: the SMS log, the counts of a day, and the texts. Phones are always masked here
 * (rule 12): a parent says "I got no message" → the office finds the child and sees {@code FAILED: invalid number}.
 */
@Service
public class MessageService {

	static final String ENTITY = "MESSAGE_TEMPLATE";

	private final MessageOutboxRepository outbox;

	private final MessageTemplateRepository templates;

	private final StudentQueryService studentQuery;

	private final AuditService auditService;

	private final Clock clock;

	public MessageService(MessageOutboxRepository outbox, MessageTemplateRepository templates,
			StudentQueryService studentQuery, AuditService auditService, Clock clock) {
		this.outbox = outbox;
		this.templates = templates;
		this.studentQuery = studentQuery;
		this.auditService = auditService;
		this.clock = clock;
	}

	/**
	 * Messages created on one day (default today), newest first.
	 *
	 * @param phone any part of a number, at least 4 digits. Example: "4321" finds +919811104321
	 * @throws ApiException 400 VALIDATION for a bad page, size or phone filter
	 */
	@Transactional(readOnly = true)
	public PageResponse<MessageResponse> list(LocalDate date, MessageStatus status, Long studentId, String phone,
			int page, int size) {
		if (page < 0) {
			throw ApiException.validation("page", "must be 0 or more");
		}
		if (size < 1 || size > 100) {
			throw ApiException.validation("size", "must be between 1 and 100");
		}
		String phoneLike = null;
		if (phone != null && !phone.isBlank()) {
			String digits = phone.replaceAll("\\D", "");
			if (digits.length() < 4) {
				throw ApiException.validation("phone", "needs at least 4 digits");
			}
			phoneLike = "%" + digits + "%";
		}
		LocalDate day = (date != null) ? date : LocalDate.now(clock);
		ZoneId zone = clock.getZone();
		Page<MessageOutbox> found = outbox.search(day.atStartOfDay(zone).toInstant(),
				day.plusDays(1).atStartOfDay(zone).toInstant(), status, studentId, phoneLike,
				PageRequest.of(page, size));
		Map<Long, String> names = studentQuery.names(found.getContent()
			.stream()
			.map(MessageOutbox::getStudentId)
			.filter(java.util.Objects::nonNull)
			.collect(Collectors.toSet()));
		return PageResponse.of(found.map(m -> toResponse(m, names.get(m.getStudentId()), zone)));
	}

	@Transactional(readOnly = true)
	public MessageSummaryResponse summary(LocalDate date) {
		LocalDate day = (date != null) ? date : LocalDate.now(clock);
		ZoneId zone = clock.getZone();
		Map<MessageStatus, Long> counts = new HashMap<>();
		for (Object[] row : outbox.countByStatus(day.atStartOfDay(zone).toInstant(),
				day.plusDays(1).atStartOfDay(zone).toInstant())) {
			counts.put((MessageStatus) row[0], ((Number) row[1]).longValue());
		}
		long total = counts.values().stream().mapToLong(Long::longValue).sum();
		return new MessageSummaryResponse(day, counts.getOrDefault(MessageStatus.QUEUED, 0L),
				counts.getOrDefault(MessageStatus.SENT, 0L), counts.getOrDefault(MessageStatus.FAILED, 0L),
				counts.getOrDefault(MessageStatus.TEST_ONLY, 0L), total);
	}

	@Transactional(readOnly = true)
	public List<TemplateResponse> templates() {
		return templates.findAllByOrderByCodeAsc().stream().map(TemplateResponse::of).toList();
	}

	/**
	 * Changes the text and the provider id of a template. The text must keep {name} and {time}, and must fit in
	 * 70 characters with a long name (rule 7).
	 *
	 * @throws ApiException 404 NOT_FOUND, 400 VALIDATION
	 */
	@Transactional
	public TemplateResponse updateTemplate(String code, UpdateTemplateRequest request) {
		MessageTemplate template = templates.findById(code)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This template does not exist."));
		String body = request.body().strip();
		if (!body.contains("{name}") || !body.contains("{time}")) {
			throw ApiException.validation("body", "must contain {name} and {time}");
		}
		if (template.getChannel() == MessageChannel.SMS) {
			try {
				// The longest first name we plan for is 12 letters. A long name is cut by SmsTextBuilder, but the
				// rest of the text must always fit.
				SmsTextBuilder.build(body, "Abcdefghijkl", LocalTime.of(14, 40));
				SmsTextBuilder.build(body, "A", LocalTime.of(14, 40));
			}
			catch (IllegalArgumentException ex) {
				throw ApiException.validation("body", "is longer than " + SmsTextBuilder.MAX_LENGTH + " characters");
			}
		}
		String providerId = (request.providerTemplateId() == null || request.providerTemplateId().isBlank()) ? null
				: request.providerTemplateId().strip();
		boolean changed = !body.equals(template.getBody())
				|| !java.util.Objects.equals(providerId, template.getProviderTemplateId())
				|| request.active() != template.isActive();
		if (changed) {
			template.change(body, providerId, request.active(), Instant.now(clock));
			auditService.record(ENTITY, 0L, AuditAction.UPDATED, "Template " + code + " changed",
					Map.of("code", code));
		}
		return TemplateResponse.of(template);
	}

	/**
	 * The boarding SMS of some children on one day, one answer per child and event. A child with two parent phones
	 * has two rows; the worst one counts: FAILED, then QUEUED, then SENT, then TEST_ONLY.
	 * A child and event with no row are not in the map.
	 */
	@Transactional(readOnly = true)
	public Map<Long, Map<com.muhjain.school.trip.EventType, SmsDelivery>> deliveries(
			java.util.Collection<Long> studentIds, LocalDate day) {
		Map<Long, Map<com.muhjain.school.trip.EventType, SmsDelivery>> result = new HashMap<>();
		if (studentIds.isEmpty()) {
			return result;
		}
		for (MessageOutbox m : outbox.boardingFor(studentIds, day)) {
			SmsState state = switch (m.getStatus()) {
				case FAILED -> SmsState.FAILED;
				case QUEUED -> SmsState.QUEUED;
				case SENT -> SmsState.SENT;
				case TEST_ONLY -> SmsState.TEST_ONLY;
			};
			SmsDelivery now = new SmsDelivery(state,
					(m.getSentAt() == null) ? null : m.getSentAt().atZone(clock.getZone()).toOffsetDateTime());
			result.computeIfAbsent(m.getStudentId(), id -> new java.util.EnumMap<>(com.muhjain.school.trip.EventType.class))
				.merge(m.getEventType(), now, MessageService::worse);
		}
		return result;
	}

	private static SmsDelivery worse(SmsDelivery a, SmsDelivery b) {
		return (rank(a.state()) >= rank(b.state())) ? a : b;
	}

	private static int rank(SmsState state) {
		return switch (state) {
			case FAILED -> 4;
			case QUEUED -> 3;
			case SENT -> 2;
			case TEST_ONLY -> 1;
			default -> 0;
		};
	}

	private static MessageResponse toResponse(MessageOutbox m, String studentName, ZoneId zone) {
		return new MessageResponse(m.getId(), m.getCreatedAt().atZone(zone).toOffsetDateTime(), m.getPurpose(),
				m.getStudentId(), studentName, PhoneNumbers.mask(m.getPhone()), m.getEventType(), m.getBody(),
				m.getStatus(), m.getAttempts(), m.getError(),
				(m.getSentAt() == null) ? null : m.getSentAt().atZone(zone).toOffsetDateTime());
	}

}

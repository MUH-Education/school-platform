package com.muhjain.school.service.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.response.FamilyContact;
import com.muhjain.school.model.dto.response.MessageStopResponse;
import com.muhjain.school.model.dto.response.PageResponse;
import com.muhjain.school.model.entity.MessageStop;
import com.muhjain.school.model.enums.AuditAction;
import com.muhjain.school.model.enums.StopReason;
import com.muhjain.school.model.repository.MessageStopRepository;
import com.muhjain.school.service.audit.AuditService;
import com.muhjain.school.service.student.GuardianService;
import com.muhjain.school.util.PhoneNumbers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The families who said "stop" to marketing and greetings. Task 11.15 adds the screens; for now the audience and
 * the guard chain read it here, so both use one rule.
 */
@Service
public class StopListService {

	private final MessageStopRepository stops;

	static final String ENTITY = "MESSAGE_STOP";

	private final GuardianService guardianService;

	private final AuditService auditService;

	private final Clock clock;

	public StopListService(MessageStopRepository stops, GuardianService guardianService, AuditService auditService,
			Clock clock) {
		this.stops = stops;
		this.guardianService = guardianService;
		this.auditService = auditService;
		this.clock = clock;
	}

	/** The stop list, newest first, with each family's name and masked phone. */
	@Transactional(readOnly = true)
	public PageResponse<MessageStopResponse> list(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw ApiException.validation("size", "page must be 0 or more, size 1 to 100");
		}
		Page<MessageStop> found = stops.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size));
		Map<Long, FamilyContact> contacts = guardianService
			.contacts(found.getContent().stream().map(MessageStop::getGuardianId).toList());
		return PageResponse.of(found.map(s -> {
			FamilyContact c = contacts.get(s.getGuardianId());
			return new MessageStopResponse(s.getGuardianId(), (c == null) ? null : c.name(),
					PhoneNumbers.mask((c == null) ? null : c.phone()), s.getReason(), s.getNotificationId(),
					s.getCreatedAt().atZone(clock.getZone()).toOffsetDateTime());
		}));
	}

	/**
	 * Takes a family off the stop list: they asked to get the school's news again. Written to the change history.
	 *
	 * @throws ApiException 404 NOT_FOUND when the family is not on the list
	 */
	@Transactional
	public void remove(Long guardianId) {
		MessageStop stop = stops.findByGuardianId(guardianId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This family is not on the stop list."));
		stops.delete(stop);
		String phone = guardianService.phones(List.of(guardianId)).get(guardianId);
		auditService.record(ENTITY, guardianId, AuditAction.DELETED, PhoneNumbers.mask(phone)
				+ " is off the stop list: marketing and greetings reach them again.", Map.of());
	}

	/**
	 * The phones of the stopped families. Matched by phone, not by guardian id, so a stopped family that is also an
	 * open enquiry (same phone) is stopped there too. The list is small (a few dozen rows).
	 */
	/**
	 * Puts a family on the stop list, once. Example: the Malik family taps "Stop messages" on the open-day message.
	 * Written to the change history with a masked phone.
	 *
	 * @param notificationId the message they answered, or null
	 * @return true when the family was added now, false when it was on the list already
	 */
	@Transactional
	public boolean add(Long guardianId, StopReason reason, Long notificationId) {
		if (stops.existsByGuardianId(guardianId)) {
			return false;
		}
		MessageStop stop = new MessageStop();
		stop.setGuardianId(guardianId);
		stop.setReason(reason);
		stop.setNotificationId(notificationId);
		stops.save(stop);
		String phone = guardianService.phones(List.of(guardianId)).get(guardianId);
		auditService.record(ENTITY, guardianId, AuditAction.CREATED, PhoneNumbers.mask(phone)
				+ " is on the stop list (" + ((reason == StopReason.TAPPED_BUTTON) ? "tapped Stop" : "wrote STOP")
				+ "): no more marketing or greetings.", Map.of("reason", reason.name()));
		return true;
	}

	@Transactional(readOnly = true)
	public Set<String> stoppedPhones() {
		List<Long> guardianIds = stops.findAll().stream().map(MessageStop::getGuardianId).toList();
		return new HashSet<>(guardianService.phones(guardianIds).values());
	}

}

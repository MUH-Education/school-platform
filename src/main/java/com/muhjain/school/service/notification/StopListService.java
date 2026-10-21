package com.muhjain.school.service.notification;

import com.muhjain.school.model.entity.MessageStop;
import com.muhjain.school.model.repository.MessageStopRepository;
import com.muhjain.school.service.student.GuardianService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The families who said "stop" to marketing and greetings. Task 11.15 adds the screens; for now the audience and
 * the guard chain read it here, so both use one rule.
 */
@Service
public class StopListService {

	private final MessageStopRepository stops;

	private final GuardianService guardianService;

	public StopListService(MessageStopRepository stops, GuardianService guardianService) {
		this.stops = stops;
		this.guardianService = guardianService;
	}

	/**
	 * The phones of the stopped families. Matched by phone, not by guardian id, so a stopped family that is also an
	 * open enquiry (same phone) is stopped there too. The list is small (a few dozen rows).
	 */
	@Transactional(readOnly = true)
	public Set<String> stoppedPhones() {
		List<Long> guardianIds = stops.findAll().stream().map(MessageStop::getGuardianId).toList();
		return new HashSet<>(guardianService.phones(guardianIds).values());
	}

}

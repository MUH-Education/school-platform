package com.muhjain.school.service.fee;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.muhjain.school.model.enums.AuditAction;
import com.muhjain.school.service.audit.AuditService;
import com.muhjain.school.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.muhjain.school.model.dto.request.CreateSessionRequest;
import com.muhjain.school.model.dto.response.SessionResponse;
import com.muhjain.school.model.entity.AcademicSession;
import com.muhjain.school.model.repository.AcademicSessionRepository;

/**
 * School years. "Current" is not a flag anyone sets: it is whichever session's dates include today.
 * Example: today is 7 Oct 2026, so "2026-27" (1 Apr 2026 to 31 Mar 2027) is current.
 */
@Service
public class AcademicSessionService {

	private final AcademicSessionRepository sessions;

	private final AuditService audit;

	private final Clock clock;

	public AcademicSessionService(AcademicSessionRepository sessions, AuditService audit, Clock clock) {
		this.sessions = sessions;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<SessionResponse> list() {
		LocalDate today = LocalDate.now(clock);
		return sessions.findAllByOrderByStartsOnDesc().stream().map(s -> SessionResponse.of(s, today)).toList();
	}

	/** The current session. Every fee rule that says "this session" uses it. */
	@Transactional(readOnly = true)
	public AcademicSession current() {
		LocalDate today = LocalDate.now(clock);
		return sessions.findFirstByStartsOnLessThanEqualAndEndsOnGreaterThanEqual(today, today)
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_CURRENT_SESSION",
					"No school year is current. Add one first."));
	}

	/**
	 * The session a report is about: the one asked for, or the current one when {@code sessionId} is null.
	 * Example: {@code resolve(null)} → 2026-27. {@code resolve(99)} → 404 NOT_FOUND.
	 */
	@Transactional(readOnly = true)
	public SessionResponse resolve(Long sessionId) {
		return SessionResponse.of((sessionId == null) ? current() : get(sessionId), LocalDate.now(clock));
	}

	/**
	 * The session an enquiry is for: the soonest one that has not started yet, or the current one if
	 * there is no later session added. Replaces the old {@code SessionNames.nextSession()}, which just
	 * computed a name without checking a session for it actually existed.
	 */
	@Transactional(readOnly = true)
	public AcademicSession nextOrCurrent() {
		LocalDate today = LocalDate.now(clock);
		return sessions.findFirstByStartsOnGreaterThanOrderByStartsOnAsc(today).orElseGet(this::current);
	}

	@Transactional(readOnly = true)
	public AcademicSession get(Long id) {
		return sessions.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This school year does not exist."));
	}

	@Transactional(readOnly = true)
	public boolean exists(Long id) {
		return sessions.existsById(id);
	}

	@Transactional
	public SessionResponse create(CreateSessionRequest request) {
		String name = request.name().strip();
		if (!name.matches("\\d{4}-\\d{2}")) {
			throw ApiException.validation("name", "must look like 2027-28");
		}
		int startYear = request.startsOn().getYear();
		int shortEnd = Integer.parseInt(name.substring(5));
		if (Integer.parseInt(name.substring(0, 4)) != startYear || shortEnd != (startYear + 1) % 100) {
			throw ApiException.validation("name", "must match the start date, for example 2027-28 for 2027-04-01");
		}
		if (!request.endsOn().isAfter(request.startsOn())) {
			throw ApiException.validation("endsOn", "must be after the start date");
		}
		if (sessions.existsByNameIgnoreCase(name)) {
			throw new ApiException(HttpStatus.CONFLICT, "SESSION_ALREADY_EXISTS",
					"The school year " + name + " exists already.");
		}
		boolean overlaps = sessions.findAll()
			.stream()
			.anyMatch(s -> !request.startsOn().isAfter(s.getEndsOn()) && !request.endsOn().isBefore(s.getStartsOn()));
		if (overlaps) {
			throw new ApiException(HttpStatus.CONFLICT, "SESSION_OVERLAP",
					"These dates overlap another school year.");
		}
		AcademicSession saved = sessions.save(new AcademicSession(name, request.startsOn(), request.endsOn()));
		audit.record("SESSION", saved.getId(), AuditAction.CREATED, "School year " + name + " added.", null);
		return SessionResponse.of(saved, LocalDate.now(clock));
	}

}

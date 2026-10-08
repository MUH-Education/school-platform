package com.muhjain.school.fee;

import java.util.List;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * School years. Rule 1 of phase 7: exactly one session is current.
 * Example: "2026-27" is current. Adding "2027-28" with {@code current: true} makes it current and "2026-27" not.
 */
@Service
public class SessionService {

	private final AcademicSessionRepository sessions;

	private final AuditService audit;

	public SessionService(AcademicSessionRepository sessions, AuditService audit) {
		this.sessions = sessions;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<SessionResponse> list() {
		return sessions.findAllByOrderByStartsOnDesc().stream().map(SessionResponse::of).toList();
	}

	/** The current session. Every fee rule that says "this session" uses it. */
	@Transactional(readOnly = true)
	public AcademicSession current() {
		return sessions.findByCurrentTrue()
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_CURRENT_SESSION",
					"No school year is current. Add one first."));
	}

	/**
	 * The session a report is about: the one asked for, or the current one when {@code sessionId} is null.
	 * Example: {@code resolve(null)} → 2026-27. {@code resolve(99)} → 404 NOT_FOUND.
	 */
	@Transactional(readOnly = true)
	public SessionResponse resolve(Long sessionId) {
		return SessionResponse.of((sessionId == null) ? current() : get(sessionId));
	}

	@Transactional(readOnly = true)
	public AcademicSession get(Long id) {
		return sessions.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This school year does not exist."));
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
		boolean makeCurrent = Boolean.TRUE.equals(request.current());
		if (makeCurrent) {
			// The old one must stop being current first: the database allows one current row at a time.
			sessions.findByCurrentTrue().ifPresent(old -> {
				old.setCurrent(false);
				sessions.saveAndFlush(old);
			});
		}
		AcademicSession saved = sessions
			.save(new AcademicSession(name, request.startsOn(), request.endsOn(), makeCurrent));
		audit.record("SESSION", saved.getId(), AuditAction.CREATED,
				"School year " + name + " added" + (makeCurrent ? " and made current." : "."), null);
		return SessionResponse.of(saved);
	}

}

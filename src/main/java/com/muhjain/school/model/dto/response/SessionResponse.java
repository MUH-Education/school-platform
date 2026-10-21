package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.entity.AcademicSession;

import java.time.LocalDate;

/** One school year. Example: {@code {"id":1,"name":"2026-27","startsOn":"2026-04-01","endsOn":"2027-03-31","current":true}} */
public record SessionResponse(Long id, String name, LocalDate startsOn, LocalDate endsOn, boolean current) {

	/** @param today used only to compute {@code current}: the session whose dates include it */
	public static SessionResponse of(AcademicSession session, LocalDate today) {
		return new SessionResponse(session.getId(), session.getName(), session.getStartsOn(), session.getEndsOn(),
				session.covers(today));
	}

}

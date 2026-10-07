package com.muhjain.school.fee;

import java.time.LocalDate;

/** One school year. Example: {@code {"id":1,"name":"2026-27","startsOn":"2026-04-01","endsOn":"2027-03-31","current":true}} */
public record SessionResponse(Long id, String name, LocalDate startsOn, LocalDate endsOn, boolean current) {

	static SessionResponse of(AcademicSession session) {
		return new SessionResponse(session.getId(), session.getName(), session.getStartsOn(), session.getEndsOn(),
				session.isCurrent());
	}

}

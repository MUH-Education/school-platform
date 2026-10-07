package com.muhjain.school.trip;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The answer for one tap. {@code error} is missing when {@code ok} is true.
 * Example: {@code {"studentId":131,"eventType":"BOARDED_MORNING","ok":false,"error":"NOT_YOUR_ROUTE"}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MarkResult(Long studentId, EventType eventType, boolean ok, String error) {

	static MarkResult ok(MarkRequest mark) {
		return new MarkResult(mark.studentId(), mark.eventType(), true, null);
	}

	static MarkResult failed(MarkRequest mark, String error) {
		return new MarkResult(mark.studentId(), mark.eventType(), false, error);
	}

}

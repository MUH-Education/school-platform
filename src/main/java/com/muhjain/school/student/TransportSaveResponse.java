package com.muhjain.school.student;

/**
 * The answer of {@code PUT /students/{id}/transport}. It is saved. {@code warning} is null unless the route is over
 * its seats. Example: {@code { "saved": true, "warning": { "code": "ROUTE_FULL", "message": "..." } }}
 */
public record TransportSaveResponse(boolean saved, TransportWarning warning) {

	static TransportSaveResponse ok(TransportWarning warning) {
		return new TransportSaveResponse(true, warning);
	}

}

package com.muhjain.school.trip;

import java.time.LocalDate;
import java.util.List;

/**
 * What the office must look at now.
 * {@code routes}: buses with NO_TAPS or LATE (only in the morning of today).
 * {@code missingInEvening}: children nobody answered for in the evening.
 */
public record AttentionResponse(LocalDate date, List<RouteStatusResponse> routes,
		List<MissingChild> missingInEvening) {

}

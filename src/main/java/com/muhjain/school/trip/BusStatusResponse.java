package com.muhjain.school.trip;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The answer of {@code GET /bus-status}. {@code date} is the day shown, {@code phase} the one actually used
 * (filled in even when the caller sent neither). {@code asOf} is the server's time right now, so the web app can
 * show "Live · updated 7:48 am".
 */
public record BusStatusResponse(LocalDate date, OffsetDateTime asOf, BusPhase phase,
		List<RouteStatusResponse> routes) {

}

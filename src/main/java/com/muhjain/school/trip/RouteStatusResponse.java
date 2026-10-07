package com.muhjain.school.trip;

import java.util.List;

/**
 * One route on Bus status. See docs/06-api.md. {@code reachedAt} is "HH:mm", the first REACHED_SCHOOL tap in the
 * morning or the first REACHED_HOME tap in the evening. {@code attendant} is null if nobody is on the vehicle that
 * day. {@code notTravelling} is only used in the evening.
 */
public record RouteStatusResponse(Long routeId, String name, String vehicle, String attendant, BusPhase phase,
		RouteState state, int lateMinutes, String reachedAt, int boarded, int absent, int notTravelling, int total,
		List<StopStatusResponse> stops) {

}

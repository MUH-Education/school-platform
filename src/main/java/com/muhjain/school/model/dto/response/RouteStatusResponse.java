package com.muhjain.school.model.dto.response;

import java.util.List;

import com.muhjain.school.model.enums.VehicleType;
import com.muhjain.school.model.enums.BusPhase;
import com.muhjain.school.model.enums.RouteState;

/**
 * One route on Bus status. See docs/06-api.md. {@code attendant} is null if nobody is on the vehicle that day.
 * {@code startsAt} is the due time of the first stop, filled only while the route is {@code NOT_STARTED}, else
 * null. {@code notTravelling} is only used in the evening.
 */
public record RouteStatusResponse(Long routeId, String name, String vehicle, VehicleType vehicleType, Integer seats,
		String attendant, BusPhase phase, RouteState state, int lateMinutes, String startsAt, School school,
		int boarded, int absent, int notTravelling, int total, List<StopStatusResponse> stops) {

	/**
	 * The last square of the row. {@code due} is the time the route is due at school, null until the office sets
	 * it on the route. {@code reachedAt} is "HH:mm", the first REACHED_SCHOOL tap in the morning or the first
	 * REACHED_HOME tap in the evening, null until then.
	 */
	public record School(String due, String reachedAt) {

	}

}

package com.muhjain.school.model.dto.response;

import java.time.LocalTime;

/**
 * A route in short for trips and bus status. {@code schoolArrivalTime}/{@code schoolLeaveTime} are the time the
 * route is due at school. Example: {@code RouteSummary(4, "Route 4", 4, 08:10, 14:35)}.
 */
public record RouteSummary(Long id, String name, Long vehicleId, LocalTime schoolArrivalTime,
		LocalTime schoolLeaveTime) {

}

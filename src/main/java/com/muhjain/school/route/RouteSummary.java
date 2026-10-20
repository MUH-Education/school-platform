package com.muhjain.school.route;

import java.time.LocalTime;

/**
 * A route in short for trips and bus status. {@code schoolDueMorning}/{@code schoolDueEvening} are the time the
 * route is due at school, null until the office sets them. Example: {@code RouteSummary(4, "Route 4", 4, 08:10,
 * 14:35)}.
 */
public record RouteSummary(Long id, String name, Long vehicleId, LocalTime schoolDueMorning,
		LocalTime schoolDueEvening) {

}

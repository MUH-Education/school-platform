package com.muhjain.school.route;

/** A route in short for trips and bus status. Example: {@code RouteSummary(4, "Route 4", 4)} (vehicle 4). */
public record RouteSummary(Long id, String name, Long vehicleId) {

}

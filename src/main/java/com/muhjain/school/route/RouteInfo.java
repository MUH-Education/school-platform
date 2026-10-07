package com.muhjain.school.route;

/**
 * A route in short, for other features. {@code seats} is the seats of its vehicle, null if the route has no vehicle.
 * Example: {@code RouteInfo(9, "Route 9", true, 26)}.
 */
public record RouteInfo(Long id, String name, boolean active, Integer seats) {

}

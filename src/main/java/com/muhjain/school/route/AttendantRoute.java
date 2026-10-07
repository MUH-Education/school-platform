package com.muhjain.school.route;

/**
 * The route an attendant works on one day.
 * Example: Balwan on 7 Oct 2026 → {@code AttendantRoute(4, "Route 4", 4, "Van 4")}.
 */
public record AttendantRoute(Long routeId, String routeName, Long vehicleId, String vehicleName) {

}

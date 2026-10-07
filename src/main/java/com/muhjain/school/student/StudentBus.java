package com.muhjain.school.student;

/**
 * Where a child is on one day, for the trip checks.
 * {@code routeId} is null when the child has no bus that day.
 * Example: Aryan on 7 Oct → {@code StudentBus(118, true, 4)}. A child who left → {@code active = false}.
 */
public record StudentBus(Long studentId, boolean active, Long routeId) {

}

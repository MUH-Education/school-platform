package com.muhjain.school.trip;

/**
 * A child who came in the morning and has no answer in the evening, after evening boarding started on the route.
 * This is the most serious warning in the system. Times are "HH:mm" in school time.
 * Example: Neha, class 3 B, Route 4, stop Jakhal. Boarding started at 14:40.
 */
public record MissingChild(Long studentId, String name, String className, String section, Long routeId,
		String routeName, String stopName, String eveningBoardingStartedAt) {

}

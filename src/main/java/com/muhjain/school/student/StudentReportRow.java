package com.muhjain.school.student;

/**
 * One active child, with the facts a report needs. {@code routeId} is the route today, null with no bus.
 * Example: Aryan, class 3, section A, Jakhal, FARMER_SMALL, Route 4.
 */
public record StudentReportRow(Long id, String name, String className, String section, String village,
		FatherOccupation fatherOccupation, Long routeId) {

}

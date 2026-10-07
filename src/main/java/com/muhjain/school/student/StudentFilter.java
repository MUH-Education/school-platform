package com.muhjain.school.student;

/**
 * What the Students list is filtered by. Every part is optional.
 * Example: {@code q = "9812"} finds a child by a phone number that contains 9812. {@code status} is ACTIVE when
 * missing (rule 22).
 */
public record StudentFilter(String q, String className, String village, Long routeId, BusFilter bus,
		StudentStatus status) {

}

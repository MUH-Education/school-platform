package com.muhjain.school.trip;

/** A child on the one-bus screen: where they board and the four events so far. */
public record RouteChildStatus(Long studentId, String name, String admissionNo, String className, String section,
		Long stopId, String stopName, EventsResponse events) {

}

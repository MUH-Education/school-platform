package com.muhjain.school.trip;

/**
 * A child on the one-bus screen: where they board, the four events so far, and the parent SMS state of each event
 * (the "SMS to parent" column).
 */
public record RouteChildStatus(Long studentId, String name, String admissionNo, String className, String section,
		Long stopId, String stopName, EventsResponse events, SmsResponse sms) {

}

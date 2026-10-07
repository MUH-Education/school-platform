package com.muhjain.school.trip;

import java.util.List;
import java.util.Map;

import com.muhjain.school.route.StopTimes;
import com.muhjain.school.student.RouteChild;

/**
 * Everything about one route on one school day: its stops (morning order), the children on it that day, and the
 * taps so far. The manifest, Bus status and the attention list all start from this.
 *
 * @param events the taps by child, then by event type. A child with no tap is not in the map.
 */
record RouteDay(Long routeId, String routeName, Long vehicleId, List<StopTimes> stops, List<RouteChild> children,
		Map<Long, Map<EventType, BoardingEvent>> events) {

	BoardingEvent tap(Long studentId, EventType type) {
		Map<EventType, BoardingEvent> byType = events.get(studentId);
		return (byType != null) ? byType.get(type) : null;
	}

}

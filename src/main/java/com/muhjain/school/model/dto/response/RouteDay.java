package com.muhjain.school.model.dto.response;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import com.muhjain.school.model.dto.response.StopTimes;
import com.muhjain.school.model.dto.response.RouteChild;
import com.muhjain.school.model.entity.BoardingEvent;
import com.muhjain.school.model.enums.EventType;

/**
 * Everything about one route on one school day: its stops (morning order), the children on it that day, and the
 * taps so far. The manifest, Bus status and the attention list all start from this.
 * {@code schoolDueMorning}/{@code schoolDueEvening} are the time the route is due at school, null until the
 * office sets them.
 *
 * @param events the taps by child, then by event type. A child with no tap is not in the map.
 */
public record RouteDay(Long routeId, String routeName, Long vehicleId, LocalTime schoolDueMorning,
		LocalTime schoolDueEvening, List<StopTimes> stops, List<RouteChild> children,
		Map<Long, Map<EventType, BoardingEvent>> events) {

	public BoardingEvent tap(Long studentId, EventType type) {
		Map<EventType, BoardingEvent> byType = events.get(studentId);
		return (byType != null) ? byType.get(type) : null;
	}

}

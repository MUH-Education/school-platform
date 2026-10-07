package com.muhjain.school.route;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * One stop of a route. {@code children} is the number of children who board there today (0 until Phase 3).
 * Example: {@code { "id": 12, "name": "Jakhal", "seqNo": 2, "morningTime": "07:40", "eveningTime": null,
 * "children": 6 }}
 */
public record StopResponse(Long id, String name, int seqNo, @JsonFormat(pattern = "HH:mm") LocalTime morningTime,
		@JsonFormat(pattern = "HH:mm") LocalTime eveningTime, int children) {

	static StopResponse of(RouteStop stop, int children) {
		return new StopResponse(stop.getId(), stop.getName(), stop.getSeqNo(), stop.getMorningTime(),
				stop.getEveningTime(), children);
	}

}

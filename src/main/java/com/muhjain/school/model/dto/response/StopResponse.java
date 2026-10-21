package com.muhjain.school.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.muhjain.school.model.entity.RouteStop;

import java.time.LocalTime;

/**
 * One stop of a route. {@code children} is the number of children who board there today (0 until Phase 3).
 * Example: {@code { "id": 12, "name": "Jakhal", "nameHindi": "जाखल", "seqNo": 2, "morningTime": "07:40",
 * "eveningTime": null, "children": 6 }}
 */
public record StopResponse(Long id, String name, String nameHindi, int seqNo,
		@JsonFormat(pattern = "HH:mm") LocalTime morningTime, @JsonFormat(pattern = "HH:mm") LocalTime eveningTime,
		int children) {

	public static StopResponse of(RouteStop stop, int children) {
	return new StopResponse(stop.getId(), stop.getName(), stop.getNameHindi(), stop.getSeqNo(), stop.getMorningTime(),
				stop.getEveningTime(), children);
	}

}

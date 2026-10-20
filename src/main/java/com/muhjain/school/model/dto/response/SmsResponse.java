package com.muhjain.school.model.dto.response;

import java.util.Map;

import com.muhjain.school.model.dto.response.SmsDelivery;
import com.muhjain.school.model.enums.EventType;

/**
 * The SMS state of the four events of one child. Example: {@code boardedMorning: {SENT, 07:42}, reachedSchool:
 * {NONE}, boardedEvening: {NOT_FOR_CLASS}, reachedHome: {NONE}}.
 */
public record SmsResponse(SmsDelivery boardedMorning, SmsDelivery reachedSchool, SmsDelivery boardedEvening,
		SmsDelivery reachedHome) {

	public static SmsResponse of(Map<EventType, SmsDelivery> byEvent) {
	return new SmsResponse(byEvent.get(EventType.BOARDED_MORNING), byEvent.get(EventType.REACHED_SCHOOL),
				byEvent.get(EventType.BOARDED_EVENING), byEvent.get(EventType.REACHED_HOME));
	}

}

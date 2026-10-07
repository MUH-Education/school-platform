package com.muhjain.school.trip;

import java.util.Map;

import com.muhjain.school.messaging.SmsDelivery;

/**
 * The SMS state of the four events of one child. Example: {@code boardedMorning: {SENT, 07:42}, reachedSchool:
 * {NONE}, boardedEvening: {NOT_FOR_CLASS}, reachedHome: {NONE}}.
 */
public record SmsResponse(SmsDelivery boardedMorning, SmsDelivery reachedSchool, SmsDelivery boardedEvening,
		SmsDelivery reachedHome) {

	static SmsResponse of(Map<EventType, SmsDelivery> byEvent) {
		return new SmsResponse(byEvent.get(EventType.BOARDED_MORNING), byEvent.get(EventType.REACHED_SCHOOL),
				byEvent.get(EventType.BOARDED_EVENING), byEvent.get(EventType.REACHED_HOME));
	}

}

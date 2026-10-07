package com.muhjain.school.trip;

import java.time.ZoneId;
import java.util.Map;

/**
 * The four events of one child on one day. A job with no tap yet is null.
 * Example: {@code boardedMorning: {DONE, 07:42}, reachedSchool: null, ...}.
 */
public record EventsResponse(TapResponse boardedMorning, TapResponse reachedSchool, TapResponse boardedEvening,
		TapResponse reachedHome) {

	static EventsResponse of(Map<EventType, BoardingEvent> taps, ZoneId zone) {
		return new EventsResponse(tap(taps, EventType.BOARDED_MORNING, zone),
				tap(taps, EventType.REACHED_SCHOOL, zone), tap(taps, EventType.BOARDED_EVENING, zone),
				tap(taps, EventType.REACHED_HOME, zone));
	}

	private static TapResponse tap(Map<EventType, BoardingEvent> taps, EventType type, ZoneId zone) {
		BoardingEvent event = (taps != null) ? taps.get(type) : null;
		return (event == null) ? null
				: new TapResponse(event.getOutcome(), event.getOccurredAt().atZone(zone).toOffsetDateTime());
	}

}

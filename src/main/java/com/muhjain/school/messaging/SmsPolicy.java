package com.muhjain.school.messaging;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.muhjain.school.trip.EventType;

/**
 * Which class gets which SMS. This is a promise the school makes to parents (docs/07-messaging.md). Pure Java.
 * <pre>
 * Nursery to 8   all four events
 * 9 and 10       REACHED_SCHOOL and BOARDED_EVENING
 * 11 and 12      none
 * </pre>
 * Example: Class 3 → all four. Class 9 → only REACHED_SCHOOL and BOARDED_EVENING. Class 11 → none.
 * A class name the policy does not know gets nothing (no SMS is safer than a wrong SMS).
 */
public final class SmsPolicy {

	private static final Set<EventType> ALL = EnumSet.allOf(EventType.class);

	private static final Set<EventType> MIDDLE = EnumSet.of(EventType.REACHED_SCHOOL, EventType.BOARDED_EVENING);

	private static final Map<String, Set<EventType>> BY_CLASS = Map.ofEntries(Map.entry("Nursery", ALL),
			Map.entry("LKG", ALL), Map.entry("UKG", ALL), Map.entry("1", ALL), Map.entry("2", ALL),
			Map.entry("3", ALL), Map.entry("4", ALL), Map.entry("5", ALL), Map.entry("6", ALL), Map.entry("7", ALL),
			Map.entry("8", ALL), Map.entry("9", MIDDLE), Map.entry("10", MIDDLE), Map.entry("11", Set.of()),
			Map.entry("12", Set.of()));

	private SmsPolicy() {
	}

	public static boolean allows(String className, EventType eventType) {
		return className != null && BY_CLASS.getOrDefault(className, Set.of()).contains(eventType);
	}

}

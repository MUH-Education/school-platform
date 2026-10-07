package com.muhjain.school.messaging;

import java.util.List;
import java.util.Set;

import com.muhjain.school.trip.EventType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** All 15 classes x 4 events. */
class SmsPolicyTest {

	private static final List<String> ALL_CLASSES = List.of("Nursery", "LKG", "UKG", "1", "2", "3", "4", "5", "6", "7",
			"8", "9", "10", "11", "12");

	private static Set<EventType> allowed(String className) {
		return Set.of(EventType.values())
			.stream()
			.filter(e -> SmsPolicy.allows(className, e))
			.collect(java.util.stream.Collectors.toSet());
	}

	@Test
	void nurseryToEightGetAllFourEvents() {
		for (String c : ALL_CLASSES.subList(0, 11)) {
			assertThat(allowed(c)).as("class " + c).containsExactlyInAnyOrder(EventType.values());
		}
	}

	@Test
	void nineAndTenGetOnlyReachedSchoolAndBoardedEvening() {
		for (String c : List.of("9", "10")) {
			assertThat(allowed(c)).as("class " + c)
				.containsExactlyInAnyOrder(EventType.REACHED_SCHOOL, EventType.BOARDED_EVENING);
		}
	}

	@Test
	void elevenAndTwelveGetNothing() {
		for (String c : List.of("11", "12")) {
			assertThat(allowed(c)).as("class " + c).isEmpty();
		}
	}

	@Test
	void unknownClassGetsNothing() {
		assertThat(allowed("13")).isEmpty();
		assertThat(allowed(null)).isEmpty();
	}

	@Test
	void everyClassOfTheSchoolIsCovered() {
		assertThat(com.muhjain.school.student.ClassNames.ALL).containsExactlyElementsOf(ALL_CLASSES);
	}

}

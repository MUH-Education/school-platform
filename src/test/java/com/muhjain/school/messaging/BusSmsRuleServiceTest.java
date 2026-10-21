package com.muhjain.school.messaging;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.model.enums.EventType;
import com.muhjain.school.service.messaging.BusSmsRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** All 15 classes x 4 events, against the seeded bus_sms_rule rows of V5. */
class BusSmsRuleServiceTest extends AbstractIntegrationTest {

	@Autowired
	private BusSmsRuleService policy;

	private static final List<String> ALL_CLASSES = List.of("Nursery", "LKG", "UKG", "1", "2", "3", "4", "5", "6", "7",
			"8", "9", "10", "11", "12");

	private Set<EventType> allowed(String className) {
		return Set.of(EventType.values())
			.stream()
			.filter(e -> policy.allows(className, e))
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
		assertThat(com.muhjain.school.util.ClassNames.ALL).containsExactlyElementsOf(ALL_CLASSES);
	}

}

package com.muhjain.school.analytics;

import java.util.List;

/**
 * Up to 8 villages with the most children, biggest first, then everything else together.
 * {@code others}: how many villages are left and how many children live there (0 and 0 when there are 8 or fewer).
 * Example: {@code {"total":290,"villages":[{"village":"Jakhal","students":58}, ...],"others":{"villages":22,"students":71}}}
 */
public record StudentsByVillageResponse(Long sessionId, String sessionName, int total, List<VillageCount> villages,
		Others others) {

	public record Others(int villages, int students) {
	}

}

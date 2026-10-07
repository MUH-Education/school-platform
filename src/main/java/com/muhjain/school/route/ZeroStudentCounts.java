package com.muhjain.school.route;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * No students exist before Phase 3, so every route has 0 children.
 * Phase 3: delete this class and add the real {@link StudentCounts} in the student package.
 */
@Component
class ZeroStudentCounts implements StudentCounts {

	@Override
	public int childrenOnRoute(Long routeId, LocalDate date) {
		return 0;
	}

	@Override
	public Map<Long, Integer> childrenByStop(Long routeId, LocalDate date) {
		return Map.of();
	}

}

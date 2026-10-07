package com.muhjain.school.route;

import java.time.LocalDate;
import java.util.Map;

/**
 * "How many children ride on this route?" Routes need the answer, but students come in Phase 3.
 * So routes ask this interface. Until Phase 3, {@link ZeroStudentCounts} answers 0 for everything.
 * Phase 3 deletes {@link ZeroStudentCounts} and adds a class in the {@code student} package that implements this
 * interface from {@code transport_enrolment} (query 1 of "Three queries used everywhere": children on route R on
 * day D, only ACTIVE students).
 */
public interface StudentCounts {

	/** Children on the route on that day. Example: Route 4 on 7 Oct → 19. */
	int childrenOnRoute(Long routeId, LocalDate date);

	/** Children per stop of the route on that day. A stop with no children may be missing from the map. */
	Map<Long, Integer> childrenByStop(Long routeId, LocalDate date);

}

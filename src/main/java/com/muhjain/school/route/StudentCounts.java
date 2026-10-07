package com.muhjain.school.route;

import java.time.LocalDate;
import java.util.Map;

/**
 * "How many children ride on this route?" Routes need the answer, but students belong to another feature.
 * So routes ask this interface, and the {@code student} package answers it ({@code StudentQueryService}, from
 * {@code transport_enrolment}: query 1 of "Three queries used everywhere", only ACTIVE students).
 * Example: the load board, {@code ROUTE_HAS_STUDENTS} and {@code STOP_HAS_STUDENTS} all use it.
 */
public interface StudentCounts {

	/** Children on the route on that day. Example: Route 4 on 7 Oct → 19. */
	int childrenOnRoute(Long routeId, LocalDate date);

	/** Children per stop of the route on that day. A stop with no children may be missing from the map. */
	Map<Long, Integer> childrenByStop(Long routeId, LocalDate date);

}

package com.muhjain.school.service.route;

import java.time.LocalDate;
import java.util.Map;
import com.muhjain.school.model.entity.Route;
import com.muhjain.school.service.student.StudentQueryService;

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

	/**
	 * The sum of the monthly bus fee of the children on the route that day. There is no more flat yearly fee
	 * setting: each child's own fee, from {@code transport_enrolment}, is added up.
	 */
	java.math.BigDecimal busFeePerMonthOnRoute(Long routeId, LocalDate date);

}

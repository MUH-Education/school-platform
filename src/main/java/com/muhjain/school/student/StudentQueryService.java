package com.muhjain.school.student;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Who is on route R on day D?" Query 1 of "Three queries used everywhere" in docs/03-data-model.md. It is
 * written **once**, here. The load board, the stop and route checks, the Phase 4 manifest and Bus status all use it.
 * <p>
 * A child is on route R on day D when a bus row of R covers D ({@code from_date <= D and (to_date is null or
 * to_date >= D)}) and the child is ACTIVE.
 * Example: Ishaan starts Route 9 on 2 Nov. {@code onRoute(9, 1 Nov)} does not list him. {@code onRoute(9, 2 Nov)} does.
 * <p>
 * It also gives the counts that {@link com.muhjain.school.route.StudentCounts} asks for, so routes need not know
 * about students.
 */
@Service
public class StudentQueryService implements com.muhjain.school.route.StudentCounts {

	private final TransportEnrolmentRepository enrolments;

	public StudentQueryService(TransportEnrolmentRepository enrolments) {
		this.enrolments = enrolments;
	}

	/** The children on the route on that day, sorted by name. */
	@Transactional(readOnly = true)
	public List<RouteChild> onRoute(Long routeId, LocalDate date) {
		return enrolments.onRoute(routeId, date);
	}

	/** Example: Route 4 on 7 Oct → 19. */
	@Override
	@Transactional(readOnly = true)
	public int childrenOnRoute(Long routeId, LocalDate date) {
		return (int) enrolments.countOnRoute(routeId, date);
	}

	/** Children per stop on that day. A stop with no children is not in the map. */
	@Override
	@Transactional(readOnly = true)
	public Map<Long, Integer> childrenByStop(Long routeId, LocalDate date) {
		Map<Long, Integer> counts = new HashMap<>();
		for (Object[] row : enrolments.countByStop(routeId, date)) {
			counts.put((Long) row[0], ((Number) row[1]).intValue());
		}
		return counts;
	}

}

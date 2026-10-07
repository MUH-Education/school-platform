package com.muhjain.school.student;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 * It also gives the counts that routes ask for ({@link EnrolmentStudentCounts} hands them to the route package),
 * so routes need not know about students.
 */
@Service
public class StudentQueryService {

	private final TransportEnrolmentRepository enrolments;

	private final StudentRepository students;

	public StudentQueryService(TransportEnrolmentRepository enrolments, StudentRepository students) {
		this.enrolments = enrolments;
		this.students = students;
	}

	/** The children on the route on that day, sorted by name. */
	@Transactional(readOnly = true)
	public List<RouteChild> onRoute(Long routeId, LocalDate date) {
		return enrolments.onRoute(routeId, date);
	}

	/** Example: Route 4 on 7 Oct → 19. */
	@Transactional(readOnly = true)
	public int childrenOnRoute(Long routeId, LocalDate date) {
		return (int) enrolments.countOnRoute(routeId, date);
	}

	/** Children per stop on that day. A stop with no children is not in the map. */
	@Transactional(readOnly = true)
	public Map<Long, Integer> childrenByStop(Long routeId, LocalDate date) {
		Map<Long, Integer> counts = new HashMap<>();
		for (Object[] row : enrolments.countByStop(routeId, date)) {
			counts.put((Long) row[0], ((Number) row[1]).intValue());
		}
		return counts;
	}

	/**
	 * For each child id: is the child ACTIVE, and on which route on that day. An id that does not exist is not in
	 * the map. Phase 4 uses it for every tap, so one query serves the whole list.
	 * Example: {118 → StudentBus(118, true, 4)}.
	 */
	@Transactional(readOnly = true)
	public Map<Long, StudentBus> busOn(Collection<Long> studentIds, LocalDate date) {
		if (studentIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Long> routes = enrolments.coveringDay(studentIds, date)
			.stream()
			.collect(Collectors.toMap(TransportEnrolment::getStudentId, TransportEnrolment::getRouteId, (a, b) -> a));
		return students.findByIdIn(studentIds)
			.stream()
			.collect(Collectors.toMap(Student::getId, s -> new StudentBus(s.getId(),
					s.getStatus() == StudentStatus.ACTIVE, routes.get(s.getId()))));
	}

}

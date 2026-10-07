package com.muhjain.school.student;

import java.time.LocalDate;
import java.util.Map;

import com.muhjain.school.route.StudentCounts;
import org.springframework.stereotype.Component;

/**
 * The real answer to the Phase 2 question "how many children ride on this route?". It only passes the question to
 * {@link StudentQueryService}, so the query is written once. Routes, the load board, {@code ROUTE_HAS_STUDENTS} and
 * {@code STOP_HAS_STUDENTS} use it through the {@link StudentCounts} interface.
 * <p>
 * It is a class of its own on purpose: a test can replace {@link StudentCounts} with a mock without removing
 * {@link StudentQueryService}, which the student services need.
 */
@Component
class EnrolmentStudentCounts implements StudentCounts {

	private final StudentQueryService queries;

	EnrolmentStudentCounts(StudentQueryService queries) {
		this.queries = queries;
	}

	@Override
	public int childrenOnRoute(Long routeId, LocalDate date) {
		return queries.childrenOnRoute(routeId, date);
	}

	@Override
	public Map<Long, Integer> childrenByStop(Long routeId, LocalDate date) {
		return queries.childrenByStop(routeId, date);
	}

}

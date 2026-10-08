package com.muhjain.school.analytics;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.fee.FeeStatusCalculator;
import com.muhjain.school.fee.FeeStatusService;
import com.muhjain.school.fee.SessionResponse;
import com.muhjain.school.fee.SessionService;
import com.muhjain.school.route.RouteService;
import com.muhjain.school.student.StudentQueryService;
import com.muhjain.school.student.StudentReportRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The start of every Analytics answer (rules 2 and 3 of phase 8). One filter in, one list of children out, with
 * their fee numbers. Summary, graphs, list and CSV all count this same list, so two graphs can never disagree.
 * <p>
 * The data is small (at most about 650 children), so it loads the children and counts in Java. No clever SQL.
 * Example: {@code village=Jakhal, feeStatus=DELAYED} → the late children of Jakhal, each with a pending amount.
 */
@Service
public class AnalyticsBase {

	private final StudentQueryService studentQuery;

	private final FeeStatusService feeStatus;

	private final SessionService sessions;

	private final RouteService routeService;

	private final Clock clock;

	public AnalyticsBase(StudentQueryService studentQuery, FeeStatusService feeStatus, SessionService sessions,
			RouteService routeService, Clock clock) {
		this.studentQuery = studentQuery;
		this.feeStatus = feeStatus;
		this.sessions = sessions;
		this.routeService = routeService;
		this.clock = clock;
	}

	/**
	 * The school year of the filter: the one asked for, or the current one.
	 *
	 * @throws ApiException 404 NOT_FOUND for a {@code sessionId} that does not exist
	 */
	@Transactional(readOnly = true)
	public SessionResponse session(StudentFilter filter) {
		return sessions.resolve(filter.sessionId());
	}

	/**
	 * The active children that match, sorted by name. With {@code feeStatus} in the filter, only children with a fee
	 * plan and that status are kept. An empty result is an empty list, not an error.
	 *
	 * @throws ApiException 404 NOT_FOUND for a {@code sessionId} or {@code routeId} that does not exist
	 */
	@Transactional(readOnly = true)
	public List<AnalyticsStudent> students(StudentFilter filter) {
		SessionResponse session = session(filter);
		if (filter.routeId() != null && routeService.info(filter.routeId()).isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This route does not exist.");
		}
		LocalDate today = LocalDate.now(clock);
		List<StudentReportRow> rows = studentQuery.activeMatching(filter.classNames(), filter.village(),
				filter.routeId(), filter.bus(), filter.occupation(), today);
		List<Long> ids = rows.stream().map(StudentReportRow::id).toList();
		Map<Long, FeeStatusCalculator.Summary> fees = feeStatus.summaries(session.id(), ids);
		Map<Long, String> routeNames = routeService
			.routeNames(rows.stream().map(StudentReportRow::routeId).filter(Objects::nonNull).distinct().toList());
		return rows.stream()
			.map(r -> new AnalyticsStudent(r.id(), r.name(), r.className(), r.section(), r.village(),
					r.fatherOccupation(), r.routeId(), (r.routeId() == null) ? null : routeNames.get(r.routeId()),
					fees.get(r.id())))
			.filter(s -> filter.feeStatus() == null || s.feeStatus() == filter.feeStatus())
			.toList();
	}

}

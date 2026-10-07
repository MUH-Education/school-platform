package com.muhjain.school.student;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.DayText;
import com.muhjain.school.fee.FeePlanService;
import com.muhjain.school.route.RouteInfo;
import com.muhjain.school.route.RouteService;
import com.muhjain.school.route.StopRef;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A child's bus history. Rules 10 to 14 of docs/phases/phase-3-students-admission.md.
 * <ul>
 * <li><b>Start:</b> no open row → insert one from {@code fromDate}.
 * Example: Ishaan joined on 1 Apr with no bus. On 2 Nov he starts Route 9. One row, from 2 Nov.</li>
 * <li><b>Change:</b> an open row → its {@code to_date} becomes {@code fromDate - 1}, a new row starts on
 * {@code fromDate}. Example: Route 4 to Route 6 on 1 Dec → the old row ends 30 Nov.</li>
 * <li><b>Stop:</b> the open row gets {@code to_date = fromDate - 1}.</li>
 * </ul>
 * "On the bus on day D" means {@code from_date <= D and (to_date is null or to_date >= D)}.
 * <p>
 * Two points the docs do not name (see docs/08-decisions.md): a row that starts on the same day as the change is
 * replaced (it never took effect), and a new row cannot start before an older row ended.
 */
@Service
public class TransportEnrolmentService {

	static final String ENTITY = "STUDENT";

	static final String ROUTE_FULL = "ROUTE_FULL";

	private final StudentRepository students;

	private final TransportEnrolmentRepository enrolments;

	private final RouteService routeService;

	private final AuditService auditService;

	private final StudentQueryService queries;

	private final FeePlanService feePlans;

	public TransportEnrolmentService(StudentRepository students, TransportEnrolmentRepository enrolments,
			RouteService routeService, AuditService auditService, StudentQueryService queries,
			FeePlanService feePlans) {
		this.feePlans = feePlans;
		this.queries = queries;
		this.students = students;
		this.enrolments = enrolments;
		this.routeService = routeService;
		this.auditService = auditService;
	}

	/** The bus of the child on that day, if any. Example: Aryan on 7 Oct → Route 4, Jakhal. */
	@Transactional(readOnly = true)
	public Optional<EnrolmentResponse> current(Long studentId, LocalDate date) {
		return enrolments.coveringDay(studentId, date).map(row -> toResponses(List.of(row)).getFirst());
	}

	/**
	 * The bus of many children on one day, as text, for the Students list. A child with no bus is not in the map.
	 * Example: {118 → "Route 4 · Jakhal"}.
	 */
	@Transactional(readOnly = true)
	public Map<Long, String> busLabels(java.util.Collection<Long> studentIds, LocalDate date) {
		if (studentIds.isEmpty()) {
			return Map.of();
		}
		List<TransportEnrolment> rows = enrolments.coveringDay(studentIds, date);
		List<EnrolmentResponse> responses = toResponses(rows);
		Map<Long, String> labels = new java.util.HashMap<>();
		for (int i = 0; i < rows.size(); i++) {
			EnrolmentResponse r = responses.get(i);
			labels.put(rows.get(i).getStudentId(), r.routeName() + " · " + r.stopName());
		}
		return labels;
	}

	/** The whole bus history, newest first. @throws ApiException 404 NOT_FOUND */
	@Transactional(readOnly = true)
	public List<EnrolmentResponse> history(Long studentId) {
		findStudent(studentId);
		return toResponses(enrolments.findByStudentIdOrderByFromDateDescIdDesc(studentId));
	}

	/**
	 * What {@code PUT /students/{id}/transport} does: stop, change or start, whichever fits the child.
	 *
	 * @throws ApiException see {@link #start}, {@link #change}, {@link #stop}
	 */
	@Transactional
	public TransportSaveResponse save(Long studentId, TransportRequest request, Long userId) {
		if (!request.usesBus()) {
			return stop(studentId, request.fromDate(), userId);
		}
		if (request.routeId() == null) {
			throw ApiException.validation("routeId", "is required when the child uses the bus");
		}
		if (request.stopId() == null) {
			throw ApiException.validation("stopId", "is required when the child uses the bus");
		}
		findStudent(studentId);
		if (enrolments.findByStudentIdAndToDateIsNull(studentId).isPresent()) {
			return change(studentId, request.routeId(), request.stopId(), request.fromDate(), request.busFee(),
					userId);
		}
		return start(studentId, request.routeId(), request.stopId(), request.fromDate(), request.busFee(), userId);
	}

	/**
	 * Rules 10 (start), 11, 12, 13.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 STUDENT_LEFT, 400 VALIDATION (route or stop), 400 STOP_NOT_ON_ROUTE,
	 * 409 FROM_DATE_TOO_EARLY, 409 BUS_ALREADY_STARTED
	 */
	@Transactional
	public TransportSaveResponse start(Long studentId, Long routeId, Long stopId, LocalDate fromDate,
			BigDecimal busFee, Long userId) {
		Student student = findActiveStudent(studentId);
		if (enrolments.findByStudentIdAndToDateIsNull(studentId).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "BUS_ALREADY_STARTED",
					"This child already uses a bus. Change the route instead.");
		}
		Target target = checkTarget(routeId, stopId);
		checkFromDate(student, fromDate);
		LocalDate lastEnd = enrolments.lastEndDate(studentId);
		if (lastEnd != null && !fromDate.isAfter(lastEnd)) {
			throw tooEarly("The last bus of this child ended on " + DayText.on(lastEnd)
					+ ". The new bus must start after that day.");
		}
		TransportSaveResponse result = insert(student, target, fromDate, busFee, userId);
		audit(studentId, "Bus started: " + target.label() + ", from " + DayText.on(fromDate),
				details("started", target, fromDate, null));
		// Rule 8 of Phase 7: the bus fee of a bus that starts later becomes BUS dues in the fee plan.
		feePlans.addBusFee(studentId, busFee, fromDate, userId);
		return result;
	}

	/**
	 * Rule 10 (change). Example: Route 4 to Route 6 on 1 Dec → the old row ends on 30 Nov.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 STUDENT_LEFT, 409 BUS_NOT_STARTED, 400 VALIDATION, 400
	 * STOP_NOT_ON_ROUTE, 409 FROM_DATE_TOO_EARLY
	 */
	@Transactional
	public TransportSaveResponse change(Long studentId, Long routeId, Long stopId, LocalDate fromDate,
			BigDecimal busFee, Long userId) {
		Student student = findActiveStudent(studentId);
		TransportEnrolment open = enrolments.findByStudentIdAndToDateIsNull(studentId)
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "BUS_NOT_STARTED",
					"This child does not use a bus yet. Start the bus first."));
		Target target = checkTarget(routeId, stopId);
		checkFromDate(student, fromDate);
		Target old = labelOf(open);
		if (open.getRouteId().equals(routeId) && open.getStopId().equals(stopId)
				&& sameFee(open.getBusFee(), busFee)) {
			// Nothing changes: no new row, no audit line.
			return TransportSaveResponse.ok(null);
		}
		closeOrDrop(open, fromDate);
		TransportSaveResponse result = insert(student, target, fromDate, busFee, userId);
		audit(studentId,
				"Bus changed from " + old.label() + " to " + target.label() + ", from " + DayText.on(fromDate),
				details("changed", target, fromDate, old));
		return result;
	}

	/**
	 * Rule 10 (stop). The last day on the bus is {@code fromDate - 1}. A child with no bus: nothing happens.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 FROM_DATE_TOO_EARLY
	 */
	@Transactional
	public TransportSaveResponse stop(Long studentId, LocalDate fromDate, Long userId) {
		findStudent(studentId);
		Optional<TransportEnrolment> open = enrolments.findByStudentIdAndToDateIsNull(studentId);
		if (open.isEmpty()) {
			return TransportSaveResponse.ok(null);
		}
		Target old = labelOf(open.get());
		closeOrDrop(open.get(), fromDate);
		audit(studentId, "Bus stopped from " + DayText.on(fromDate) + " (was " + old.label() + ")",
				details("stopped", null, fromDate, old));
		return TransportSaveResponse.ok(null);
	}

	/**
	 * Closes the open bus of a child who leaves school (rule 17). The last day on the bus is the day the child
	 * leaves. A row that would start after that day never took effect, so it is removed.
	 * Example: leaves on 20 Oct, bus open since 1 Apr → the row ends on 20 Oct. No audit line, the caller writes one.
	 *
	 * @return true if a bus row was closed
	 */
	@Transactional
	public boolean closeForLeaving(Long studentId, LocalDate leftOn) {
		Optional<TransportEnrolment> found = enrolments.findByStudentIdAndToDateIsNull(studentId);
		found.ifPresent(open -> {
			if (leftOn.isBefore(open.getFromDate())) {
				enrolments.delete(open);
			}
			else {
				open.setToDate(leftOn);
				enrolments.save(open);
			}
			enrolments.flush();
		});
		return found.isPresent();
	}

	// ---- helpers ----

	private void closeOrDrop(TransportEnrolment open, LocalDate fromDate) {
		if (fromDate.isBefore(open.getFromDate())) {
			throw tooEarly("The bus started on " + DayText.on(open.getFromDate())
					+ ". The change cannot start before that day.");
		}
		if (fromDate.equals(open.getFromDate())) {
			// The row would have no day at all. It never took effect, so it is replaced.
			enrolments.delete(open);
		}
		else {
			open.setToDate(fromDate.minusDays(1));
			enrolments.save(open);
		}
		// Written now, because the database allows only one open row per child.
		enrolments.flush();
	}

	private TransportSaveResponse insert(Student student, Target target, LocalDate fromDate, BigDecimal busFee,
			Long userId) {
		BigDecimal fee = (busFee == null) ? null : busFee.setScale(2, java.math.RoundingMode.HALF_UP);
		enrolments.saveAndFlush(new TransportEnrolment(student.getId(), target.route().id(), target.stop().id(),
				fromDate, fee, userId));
		return TransportSaveResponse.ok(fullWarning(target.route(), fromDate));
	}

	// Rule 13: over the seats is a warning, not an error. Example: "Route 9 has 27 children on 26 seats."
	private TransportWarning fullWarning(RouteInfo route, LocalDate day) {
		if (route.seats() == null) {
			return null;
		}
		int children = queries.childrenOnRoute(route.id(), day);
		if (children <= route.seats()) {
			return null;
		}
		return new TransportWarning(ROUTE_FULL,
				route.name() + " has " + children + " children on " + route.seats() + " seats.");
	}

	// Rules 11: the stop must belong to the route. The route must be on.
	private Target checkTarget(Long routeId, Long stopId) {
		RouteInfo route = routeService.info(routeId)
			.orElseThrow(() -> ApiException.validation("routeId", "does not exist"));
		if (!route.active()) {
			throw ApiException.validation("routeId", route.name() + " is turned off");
		}
		StopRef stop = routeService.stopRefs(Set.of(stopId)).get(stopId);
		if (stop == null) {
			throw ApiException.validation("stopId", "does not exist");
		}
		if (!stop.routeId().equals(routeId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "STOP_NOT_ON_ROUTE",
					stop.name() + " is not a stop of " + route.name() + ".");
		}
		return new Target(route, stop);
	}

	// Rule 12.
	private static void checkFromDate(Student student, LocalDate fromDate) {
		if (fromDate.isBefore(student.getJoinedOn())) {
			throw tooEarly("The child joined on " + DayText.on(student.getJoinedOn())
					+ ". The bus cannot start before that day.");
		}
	}

	private Target labelOf(TransportEnrolment row) {
		RouteInfo route = routeService.info(row.getRouteId()).orElseThrow();
		StopRef stop = routeService.stopRefs(Set.of(row.getStopId())).get(row.getStopId());
		return new Target(route, stop);
	}

	private List<EnrolmentResponse> toResponses(List<TransportEnrolment> rows) {
		Map<Long, String> routeNames = routeService
			.routeNames(rows.stream().map(TransportEnrolment::getRouteId).collect(Collectors.toSet()));
		Map<Long, StopRef> stops = routeService
			.stopRefs(rows.stream().map(TransportEnrolment::getStopId).collect(Collectors.toSet()));
		return rows.stream()
			.map(r -> new EnrolmentResponse(r.getId(), r.getRouteId(), routeNames.get(r.getRouteId()), r.getStopId(),
					(stops.get(r.getStopId()) != null) ? stops.get(r.getStopId()).name() : null, r.getFromDate(),
					r.getToDate(), r.getBusFee(), r.getToDate() == null))
			.toList();
	}

	private void audit(Long studentId, String summary, Map<String, Object> details) {
		auditService.record(ENTITY, studentId, AuditAction.UPDATED, summary, details);
	}

	private static Map<String, Object> details(String what, Target now, LocalDate fromDate, Target before) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("bus", what);
		details.put("fromDate", fromDate.toString());
		if (now != null) {
			details.put("routeId", now.route().id());
			details.put("stopId", now.stop().id());
		}
		if (before != null) {
			details.put("oldRouteId", before.route().id());
			details.put("oldStopId", before.stop().id());
		}
		return details;
	}

	private static boolean sameFee(BigDecimal a, BigDecimal b) {
		return (a == null || b == null) ? a == b : a.compareTo(b) == 0;
	}

	private static ApiException tooEarly(String message) {
		return new ApiException(HttpStatus.CONFLICT, "FROM_DATE_TOO_EARLY", message);
	}

	private Student findStudent(Long studentId) {
		return students.findById(studentId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student does not exist."));
	}

	private Student findActiveStudent(Long studentId) {
		Student student = findStudent(studentId);
		if (student.getStatus() != StudentStatus.ACTIVE) {
			throw new ApiException(HttpStatus.CONFLICT, "STUDENT_LEFT", "This child has left the school.");
		}
		return student;
	}

	// Route and stop together. Example: "Route 9, Model Town".
	private record Target(RouteInfo route, StopRef stop) {

		String label() {
			return route.name() + ", " + stop.name();
		}

	}

}

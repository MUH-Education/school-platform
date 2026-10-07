package com.muhjain.school.trip;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.route.StopTimes;
import com.muhjain.school.setting.SettingService;
import com.muhjain.school.staff.AssignmentService;
import com.muhjain.school.staff.Crew;
import com.muhjain.school.student.RouteChild;
import com.muhjain.school.trip.BusStatusCalculator.StopInput;
import com.muhjain.school.vehicle.VehicleService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bus status for the office (rules 14 to 19). Nothing is stored: every answer is worked out from the taps.
 * The maths is in {@link BusStatusCalculator}. This class only collects the facts.
 * Example: at 7:48 it shows 9 routes, Route 3 NO_TAPS, Route 5 LATE, Route 9 NOT_STARTED, Route 2 REACHED_SCHOOL.
 */
@Service
public class BusStatusService {

	private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

	private static final LocalTime NOON = LocalTime.NOON;

	private final RouteDayService routeDays;

	private final SettingService settings;

	private final VehicleService vehicleService;

	private final AssignmentService assignmentService;

	private final Clock clock;

	public BusStatusService(RouteDayService routeDays, SettingService settings, VehicleService vehicleService,
			AssignmentService assignmentService, Clock clock) {
		this.routeDays = routeDays;
		this.settings = settings;
		this.vehicleService = vehicleService;
		this.assignmentService = assignmentService;
		this.clock = clock;
	}

	/** Rule 18: without a phase, MORNING before 12:00 and EVENING after. */
	public BusPhase defaultPhase() {
		return LocalTime.now(clock).isBefore(NOON) ? BusPhase.MORNING : BusPhase.EVENING;
	}

	public LocalDate today() {
		return LocalDate.now(clock);
	}

	/** Every active route, oldest first. */
	@Transactional(readOnly = true)
	public List<RouteStatusResponse> all(LocalDate date, BusPhase phase) {
		LocalDate day = (date != null) ? date : today();
		BusPhase effective = (phase != null) ? phase : defaultPhase();
		List<Computed> computed = computeAll(day, effective);
		return respond(computed, day);
	}

	/**
	 * One route with every child and the four events.
	 *
	 * @throws ApiException 404 NOT_FOUND
	 */
	@Transactional(readOnly = true)
	public RouteDetailResponse route(Long routeId, LocalDate date, BusPhase phase) {
		LocalDate day = (date != null) ? date : today();
		BusPhase effective = (phase != null) ? phase : defaultPhase();
		RouteDay routeDay = routeDays.load(routeId, day)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This route does not exist."));
		Computed computed = compute(routeDay, day, effective, lateAfterMinutes());
		RouteStatusResponse status = respond(List.of(computed), day).get(0);
		Map<Long, String> stopNames = routeDay.stops().stream().collect(Collectors.toMap(StopTimes::id, StopTimes::name));
		List<RouteChildStatus> children = routeDay.children()
			.stream()
			.map(c -> new RouteChildStatus(c.studentId(), c.name(), c.admissionNo(), c.className(), c.section(),
					c.stopId(), stopNames.get(c.stopId()),
					EventsResponse.of(routeDay.events().get(c.studentId()), clock.getZone())))
			.toList();
		return new RouteDetailResponse(day, status, children);
	}

	/** The status of every active route on a day, for the attention list too. */
	List<Computed> computeAll(LocalDate day, BusPhase phase) {
		int lateAfter = lateAfterMinutes();
		return routeDays.loadAllActive(day).stream().map(route -> compute(route, day, phase, lateAfter)).toList();
	}

	/** The status and counts of one route. Package-private: {@link AttentionService} uses it. */
	Computed compute(RouteDay route, LocalDate day, BusPhase phase, int lateAfterMinutes) {
		LocalDateTime now = LocalDateTime.now(clock);
		Counts counts = counts(route, phase);
		BusStatusCalculator.Status status;
		if (phase == BusPhase.MORNING) {
			List<StopInput> stops = route.stops()
				.stream()
				.map(s -> new StopInput(s.id(), s.name(), at(day, s.morningTime()),
						firstTap(route, s.id(), EventType.BOARDED_MORNING)))
				.toList();
			status = BusStatusCalculator.morning(stops, firstTapOfRoute(route, EventType.REACHED_SCHOOL), now,
					lateAfterMinutes);
		}
		else {
			List<StopInput> stops = new ArrayList<>();
			for (int i = route.stops().size() - 1; i >= 0; i--) {
				StopTimes s = route.stops().get(i);
				stops.add(new StopInput(s.id(), s.name(), at(day, s.eveningTime()),
						firstTap(route, s.id(), EventType.REACHED_HOME)));
			}
			status = BusStatusCalculator.evening(stops, counts.total(), counts.answered(), counts.boarded(),
					counts.boardedAtHome(), firstTapOfRoute(route, EventType.REACHED_HOME));
		}
		return new Computed(route, status, counts);
	}

	int lateAfterMinutes() {
		return Integer.parseInt(settings.value("transport.late_after_minutes"));
	}

	/** What Bus status knows about one route. */
	record Computed(RouteDay route, BusStatusCalculator.Status status, Counts counts) {

	}

	/**
	 * Numbers for one phase. {@code answered} (evening): children with any BOARDED_EVENING tap.
	 * {@code boardedAtHome}: children boarded in the evening who also have a REACHED_HOME tap.
	 */
	record Counts(int total, int boarded, int absent, int notTravelling, int answered, int boardedAtHome) {

	}

	private Counts counts(RouteDay route, BusPhase phase) {
		EventType type = (phase == BusPhase.MORNING) ? EventType.BOARDED_MORNING : EventType.BOARDED_EVENING;
		int boarded = 0;
		int absent = 0;
		int notTravelling = 0;
		int answered = 0;
		int boardedAtHome = 0;
		for (RouteChild child : route.children()) {
			BoardingEvent tap = route.tap(child.studentId(), type);
			if (tap == null) {
				continue;
			}
			answered++;
			switch (tap.getOutcome()) {
				case DONE -> {
					boarded++;
					if (route.tap(child.studentId(), EventType.REACHED_HOME) != null) {
						boardedAtHome++;
					}
				}
				case ABSENT -> absent++;
				case NOT_TRAVELLING -> notTravelling++;
				default -> {
				}
			}
		}
		return new Counts(route.children().size(), boarded, absent, notTravelling, answered, boardedAtHome);
	}

	List<RouteStatusResponse> respond(Collection<Computed> computed, LocalDate day) {
		List<Long> vehicleIds = computed.stream().map(c -> c.route().vehicleId()).filter(Objects::nonNull).distinct().toList();
		Map<Long, String> vehicleNames = vehicleService.names(vehicleIds);
		Map<Long, Crew> crews = assignmentService.onDate(vehicleIds, day);
		return computed.stream().map(c -> {
			Long vehicleId = c.route().vehicleId();
			Crew crew = (vehicleId != null) ? crews.get(vehicleId) : null;
			String attendant = (crew != null && crew.attendant() != null) ? crew.attendant().name() : null;
			return toResponse(c, (vehicleId != null) ? vehicleNames.get(vehicleId) : null, attendant);
		}).toList();
	}

	private static RouteStatusResponse toResponse(Computed c, String vehicle, String attendant) {
		BusStatusCalculator.Status s = c.status();
		List<StopStatusResponse> stops = s.stops()
			.stream()
			.map(st -> new StopStatusResponse(st.id(), st.name(), hhmm(st.due()), hhmm(st.tappedAt()), st.state()))
			.toList();
		return new RouteStatusResponse(c.route().routeId(), c.route().routeName(), vehicle, attendant, s.phase(),
				s.state(), s.lateMinutes(), hhmm(s.reachedAt()), c.counts().boarded(), c.counts().absent(),
				c.counts().notTravelling(), c.counts().total(), stops);
	}

	private static String hhmm(LocalDateTime time) {
		return (time == null) ? null : time.format(HOUR_MINUTE);
	}

	private static LocalDateTime at(LocalDate day, LocalTime time) {
		return (time == null) ? null : day.atTime(time);
	}

	// The time of the first tap (any answer) of the given type at one stop.
	private LocalDateTime firstTap(RouteDay route, Long stopId, EventType type) {
		return route.children()
			.stream()
			.filter(c -> stopId.equals(c.stopId()))
			.map(c -> route.tap(c.studentId(), type))
			.filter(Objects::nonNull)
			.map(BoardingEvent::getOccurredAt)
			.min(Comparator.naturalOrder())
			.map(this::local)
			.orElse(null);
	}

	// The time of the first tap of the given type on the whole route.
	private LocalDateTime firstTapOfRoute(RouteDay route, EventType type) {
		return route.children()
			.stream()
			.map(c -> route.tap(c.studentId(), type))
			.filter(Objects::nonNull)
			.map(BoardingEvent::getOccurredAt)
			.min(Comparator.naturalOrder())
			.map(this::local)
			.orElse(null);
	}

	LocalDateTime local(Instant instant) {
		return LocalDateTime.ofInstant(instant, clock.getZone());
	}

}

package com.muhjain.school.trip;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.muhjain.school.route.StopTimes;
import com.muhjain.school.student.RouteChild;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The attention list (rules 20 to 22).
 * <ul>
 * <li>NO_TAPS and LATE routes of the morning (rule 15). Shown on today's morning only, because a morning
 * warning is useless in the afternoon.</li>
 * <li>Missing in the evening: the route has at least one BOARDED_EVENING tap, and a child who came in the morning
 * (BOARDED_MORNING DONE or REACHED_SCHOOL DONE) has no evening tap at all. A child marked NOT_TRAVELLING or ABSENT
 * in the evening is not missing: someone answered for them.</li>
 * </ul>
 * Example: Neha boarded in the morning. Evening boarding started at 14:40. At 14:50 Neha has no evening tap →
 * she is on the list with her class and stop.
 */
@Service
public class AttentionService {

	private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

	private final BusStatusService busStatus;

	public AttentionService(BusStatusService busStatus) {
		this.busStatus = busStatus;
	}

	@Transactional(readOnly = true)
	public AttentionResponse attention(LocalDate date) {
		LocalDate day = (date != null) ? date : busStatus.today();
		// One load of every route. The morning status also gives us the route's children and taps.
		List<BusStatusService.Computed> computed = busStatus.computeAll(day, BusPhase.MORNING);
		boolean morningNow = day.equals(busStatus.today()) && busStatus.defaultPhase() == BusPhase.MORNING;
		List<BusStatusService.Computed> late = morningNow ? computed.stream()
			.filter(c -> c.status().state() == RouteState.NO_TAPS || c.status().state() == RouteState.LATE)
			.toList() : List.of();
		List<MissingChild> missing = new ArrayList<>();
		for (BusStatusService.Computed c : computed) {
			missing.addAll(missingIn(c.route()));
		}
		return new AttentionResponse(day, busStatus.respond(late, day), missing);
	}

	private List<MissingChild> missingIn(RouteDay route) {
		Optional<java.time.Instant> started = route.children()
			.stream()
			.map(c -> route.tap(c.studentId(), EventType.BOARDED_EVENING))
			.filter(Objects::nonNull)
			.map(BoardingEvent::getOccurredAt)
			.min(Comparator.naturalOrder());
		if (started.isEmpty()) {
			return List.of(); // Nobody is missing before evening boarding starts.
		}
		String since = busStatus.local(started.get()).format(HOUR_MINUTE);
		Map<Long, String> stopNames = route.stops().stream().collect(Collectors.toMap(StopTimes::id, StopTimes::name));
		List<MissingChild> missing = new ArrayList<>();
		for (RouteChild child : route.children()) {
			if (cameInMorning(route, child) && route.tap(child.studentId(), EventType.BOARDED_EVENING) == null
					&& route.tap(child.studentId(), EventType.REACHED_HOME) == null) {
				missing.add(new MissingChild(child.studentId(), child.name(), child.className(), child.section(),
						route.routeId(), route.routeName(), stopNames.get(child.stopId()), since));
			}
		}
		return missing;
	}

	private static boolean cameInMorning(RouteDay route, RouteChild child) {
		return isDone(route.tap(child.studentId(), EventType.BOARDED_MORNING))
				|| isDone(route.tap(child.studentId(), EventType.REACHED_SCHOOL));
	}

	private static boolean isDone(BoardingEvent tap) {
		return tap != null && tap.getOutcome() == Outcome.DONE;
	}

}

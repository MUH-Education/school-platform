package com.muhjain.school.service.trip;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.muhjain.school.model.dto.response.StopTimes;
import com.muhjain.school.model.dto.response.RouteChild;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.muhjain.school.model.dto.response.AttentionEntry;
import com.muhjain.school.model.dto.response.MissingChild;
import com.muhjain.school.model.dto.response.RouteDay;
import com.muhjain.school.model.dto.response.RouteStatusResponse;
import com.muhjain.school.model.dto.response.StopStatusResponse;
import com.muhjain.school.model.entity.BoardingEvent;
import com.muhjain.school.model.enums.AttentionKind;
import com.muhjain.school.model.enums.BusPhase;
import com.muhjain.school.model.enums.EventType;
import com.muhjain.school.model.enums.Outcome;
import com.muhjain.school.model.enums.RouteState;
import com.muhjain.school.model.enums.StopState;

/**
 * The attention list (rules 20 to 22). One flat list of plain English sentences, so the wording lives in one
 * place (here), not in the web app.
 * <ul>
 * <li>NO_TAPS and LATE routes of the morning (rule 15). Shown on today's morning only, because a morning
 * warning is useless in the afternoon.</li>
 * <li>Missing in the evening: the route has at least one BOARDED_EVENING tap, and a child who came in the morning
 * (BOARDED_MORNING DONE or REACHED_SCHOOL DONE) has no evening tap at all. A child marked NOT_TRAVELLING or ABSENT
 * in the evening is not missing: someone answered for them.</li>
 * </ul>
 * Example: Neha boarded in the morning. Evening boarding started at 14:40. At 14:50 Neha has no evening tap →
 * she is on the list with a CHILD_MISSING entry.
 */
@Service
public class AttentionService {

	private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

	private final BusStatusService busStatus;

	public AttentionService(BusStatusService busStatus) {
		this.busStatus = busStatus;
	}

	@Transactional(readOnly = true)
	public List<AttentionEntry> attention(LocalDate date) {
		LocalDate day = (date != null) ? date : busStatus.today();
		// One load of every route. The morning status also gives us the route's children and taps.
		List<BusStatusService.Computed> computed = busStatus.computeAll(day, BusPhase.MORNING);
		boolean morningNow = day.equals(busStatus.today()) && busStatus.defaultPhase() == BusPhase.MORNING;
		List<BusStatusService.Computed> late = morningNow ? computed.stream()
			.filter(c -> c.status().state() == RouteState.NO_TAPS || c.status().state() == RouteState.LATE)
			.toList() : List.of();
		List<AttentionEntry> entries = new ArrayList<>();
		for (RouteStatusResponse route : busStatus.respond(late, day)) {
			entries.add(routeEntry(route));
		}
		for (BusStatusService.Computed c : computed) {
			for (MissingChild child : missingIn(c.route())) {
				entries.add(childEntry(child));
			}
		}
		return entries;
	}

	// NO_TAPS: no stop has a tap yet, compared with the first stop. LATE: the last tapped stop, compared with its due.
	private static AttentionEntry routeEntry(RouteStatusResponse route) {
		if (route.state() == RouteState.NO_TAPS) {
			StopStatusResponse first = route.stops().get(0);
			String title = route.name() + " has no taps yet";
			String message = "The first stop, " + first.name() + ", was due at " + first.due() + ". That is "
					+ route.lateMinutes() + " minutes ago. Attendant: " + attendantOrNone(route) + ".";
			return new AttentionEntry(route.routeId(), AttentionKind.NO_TAPS, title, message);
		}
		StopStatusResponse lastDone = lastDoneStop(route.stops());
		String title = route.name() + " is late";
		String message = (lastDone != null)
				? lastDone.name() + " was due at " + lastDone.due() + ", tapped at " + lastDone.tappedAt()
						+ ". That is " + route.lateMinutes() + " minutes late. Attendant: " + attendantOrNone(route)
						+ "."
				: route.name() + " is " + route.lateMinutes() + " minutes late. Attendant: " + attendantOrNone(route)
						+ ".";
		return new AttentionEntry(route.routeId(), AttentionKind.LATE, title, message);
	}

	private static AttentionEntry childEntry(MissingChild child) {
		String title = child.name() + " has no evening tap";
		String message = child.name() + ", class " + classLabel(child.className(), child.section()) + ", stop "
				+ child.stopName() + " on " + child.routeName() + ". Evening boarding started at "
				+ child.eveningBoardingStartedAt() + ".";
		return new AttentionEntry(child.routeId(), AttentionKind.CHILD_MISSING, title, message);
	}

	private static String classLabel(String className, String section) {
		return (section != null && !section.isBlank()) ? className + "-" + section : className;
	}

	private static String attendantOrNone(RouteStatusResponse route) {
		return (route.attendant() != null) ? route.attendant() : "not assigned";
	}

	// The last stop tapped so far (rule 14's DONE stops), the one rule 16 compares against its due time.
	private static StopStatusResponse lastDoneStop(List<StopStatusResponse> stops) {
		StopStatusResponse last = null;
		for (StopStatusResponse stop : stops) {
			if (stop.state() == StopState.DONE) {
				last = stop;
			}
		}
		return last;
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

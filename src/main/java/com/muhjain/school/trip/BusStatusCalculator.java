package com.muhjain.school.trip;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The maths of Bus status (rules 14 to 17 of the phase file). Pure Java: no Spring, no database, no clock.
 * The caller gives the time {@code now} (T), so a test can use a fixed 7:48.
 * <p>
 * Example at 7:48 with {@code lateAfterMinutes} = 10:
 * <ul>
 * <li>Route 3: no taps, first stop due 7:15 → NO_TAPS, 33 minutes late.</li>
 * <li>Route 5: Samain due 7:24, tapped 7:40 → 16 minutes → LATE.</li>
 * <li>Route 9: no taps, first stop due 7:50 → NOT_STARTED.</li>
 * <li>Route 2: a REACHED_SCHOOL tap at 7:46 → REACHED_SCHOOL.</li>
 * </ul>
 */
public final class BusStatusCalculator {

	private BusStatusCalculator() {
	}

	/**
	 * One stop in the order the bus drives it. {@code due} and {@code tappedAt} may be null (a stop with no time
	 * set, or no tap yet). {@code tappedAt} is the time of the first tap at this stop, whatever the answer.
	 */
	public record StopInput(Long id, String name, LocalDateTime due, LocalDateTime tappedAt) {

	}

	/** One stop of the answer. Example: Jakhal, due 07:40, tapped 07:42, DONE. */
	public record StopStatus(Long id, String name, LocalDateTime due, LocalDateTime tappedAt, StopState state) {

	}

	/**
	 * The answer for one route. {@code lateMinutes} is 0 when the bus is not late. {@code reachedAt} is the time of
	 * the first REACHED_SCHOOL (morning) or REACHED_HOME (evening) tap, or null.
	 */
	public record Status(BusPhase phase, RouteState state, int lateMinutes, LocalDateTime reachedAt,
			List<StopStatus> stops) {

	}

	/**
	 * Morning. {@code stops} are in morning order, with the time of the first BOARDED_MORNING tap at each.
	 *
	 * @param reachedAt time of the first REACHED_SCHOOL tap, null if none
	 */
	public static Status morning(List<StopInput> stops, LocalDateTime reachedAt, LocalDateTime now,
			int lateAfterMinutes) {
		List<StopStatus> statuses = stopStates(stops);
		if (reachedAt != null) {
			return new Status(BusPhase.MORNING, RouteState.REACHED_SCHOOL, 0, reachedAt, statuses);
		}
		int lastDone = lastDoneIndex(stops);
		if (lastDone < 0) {
			// No taps. Compare with the first stop time (rule 15).
			LocalDateTime firstDue = stops.stream()
				.map(StopInput::due)
				.filter(java.util.Objects::nonNull)
				.findFirst()
				.orElse(null);
			if (firstDue == null) {
				return new Status(BusPhase.MORNING, RouteState.NOT_STARTED, 0, null, statuses);
			}
			int late = Math.max(0, minutes(firstDue, now));
			RouteState state = (late >= lateAfterMinutes) ? RouteState.NO_TAPS : RouteState.NOT_STARTED;
			return new Status(BusPhase.MORNING, state, late, null, statuses);
		}
		// Taps exist. lateMinutes is the larger of the two numbers of rule 16.
		int late = 0;
		StopInput done = stops.get(lastDone);
		if (done.due() != null) {
			late = Math.max(late, minutes(done.due(), done.tappedAt()));
		}
		if (lastDone + 1 < stops.size()) {
			StopInput next = stops.get(lastDone + 1);
			if (next.due() != null) {
				late = Math.max(late, minutes(next.due(), now));
			}
		}
		RouteState state = (late >= lateAfterMinutes) ? RouteState.LATE : RouteState.ON_THE_WAY;
		return new Status(BusPhase.MORNING, state, late, null, statuses);
	}

	/**
	 * Evening. Children board the bus at school (BOARDED_EVENING), then the bus drops them at their stops, the
	 * morning order reversed (REACHED_HOME). So {@code stops} are in evening order (last morning stop first), with
	 * the time of the first REACHED_HOME tap at each.
	 * <ul>
	 * <li>NOT_STARTED: no one has been answered for yet.</li>
	 * <li>BOARDING: some children are answered, some are not.</li>
	 * <li>ON_THE_WAY: everyone is answered, or the first child is already home.</li>
	 * <li>DONE: every child who boarded has a REACHED_HOME tap.</li>
	 * </ul>
	 *
	 * @param children children on the route that day
	 * @param answered children with any BOARDED_EVENING tap (boarded, absent or not travelling)
	 * @param boarded children whose BOARDED_EVENING tap is DONE
	 * @param boardedAtHome of those, how many have a REACHED_HOME tap
	 * @param reachedAt time of the first REACHED_HOME tap, null if none
	 */
	public static Status evening(List<StopInput> stops, int children, int answered, int boarded, int boardedAtHome,
			LocalDateTime reachedAt) {
		List<StopStatus> statuses = stopStates(stops);
		RouteState state;
		if (boarded > 0 && boardedAtHome >= boarded) {
			state = RouteState.DONE;
		}
		else if (reachedAt != null || (children > 0 && answered >= children)) {
			state = RouteState.ON_THE_WAY;
		}
		else if (answered > 0) {
			state = RouteState.BOARDING;
		}
		else {
			state = RouteState.NOT_STARTED;
		}
		return new Status(BusPhase.EVENING, state, 0, reachedAt, statuses);
	}

	// Rule 14: the last stop that has a tap, and every stop before it, are DONE. The next one is NEXT.
	private static List<StopStatus> stopStates(List<StopInput> stops) {
		int lastDone = lastDoneIndex(stops);
		List<StopStatus> result = new ArrayList<>(stops.size());
		for (int i = 0; i < stops.size(); i++) {
			StopInput stop = stops.get(i);
			StopState state = (i <= lastDone) ? StopState.DONE : (i == lastDone + 1) ? StopState.NEXT : StopState.LATER;
			result.add(new StopStatus(stop.id(), stop.name(), stop.due(), stop.tappedAt(), state));
		}
		return result;
	}

	private static int lastDoneIndex(List<StopInput> stops) {
		int last = -1;
		for (int i = 0; i < stops.size(); i++) {
			if (stops.get(i).tappedAt() != null) {
				last = i;
			}
		}
		return last;
	}

	// Whole minutes from a to b. Negative if b is before a.
	private static int minutes(LocalDateTime a, LocalDateTime b) {
		return (int) Duration.between(a, b).toMinutes();
	}

}

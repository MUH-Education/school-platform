package com.muhjain.school.trip;

import java.time.LocalDateTime;
import java.util.List;

import com.muhjain.school.trip.BusStatusCalculator.Status;
import com.muhjain.school.trip.BusStatusCalculator.StopInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure tests. The time is fixed: 7 Oct 2026, 7:48. late_after_minutes is 10. */
class BusStatusCalculatorTest {

	private static final LocalDateTime NOW = at("07:48");

	private static final int LATE_AFTER = 10;

	private static LocalDateTime at(String time) {
		return LocalDateTime.parse("2026-10-07T" + time);
	}

	private static StopInput stop(long id, String name, String due, String tapped) {
		return new StopInput(id, name, at(due), (tapped == null) ? null : at(tapped));
	}

	private static Status morning(LocalDateTime reached, StopInput... stops) {
		return BusStatusCalculator.morning(List.of(stops), reached, NOW, LATE_AFTER);
	}

	@Test
	void noTapsBeforeFirstStopIsNotStarted() {
		// Route 9: first stop due 7:50, now 7:48.
		Status status = morning(null, stop(1, "Model Town", "07:50", null), stop(2, "Bus stand", "08:05", null));
		assertThat(status.state()).isEqualTo(RouteState.NOT_STARTED);
		assertThat(status.lateMinutes()).isZero();
	}

	@Test
	void noTapsLessThanLateAfterMinutesAfterFirstStopIsStillNotStarted() {
		// Due 7:42, now 7:48: 6 minutes, below 10.
		assertThat(morning(null, stop(1, "A", "07:42", null)).state()).isEqualTo(RouteState.NOT_STARTED);
	}

	@Test
	void noTaps33MinutesAfterFirstStopIsNoTaps() {
		// Route 3: first stop due 7:15.
		Status status = morning(null, stop(1, "Dhand", "07:15", null), stop(2, "Ratia", "07:35", null));
		assertThat(status.state()).isEqualTo(RouteState.NO_TAPS);
		assertThat(status.lateMinutes()).isEqualTo(33);
		assertThat(status.stops()).extracting(BusStatusCalculator.StopStatus::state)
			.containsExactly(StopState.NEXT, StopState.LATER);
	}

	@Test
	void noTapsExactlyLateAfterMinutesAfterFirstStopIsNoTaps() {
		assertThat(morning(null, stop(1, "A", "07:38", null)).state()).isEqualTo(RouteState.NO_TAPS);
	}

	@Test
	void stopTapped16MinutesLateIsLate() {
		// Route 5: Samain due 7:24, tapped 7:40.
		Status status = morning(null, stop(1, "Samain", "07:24", "07:40"), stop(2, "Kheri", "07:55", null));
		assertThat(status.state()).isEqualTo(RouteState.LATE);
		assertThat(status.lateMinutes()).isEqualTo(16);
	}

	@Test
	void busOnTimeWithTapsIsOnTheWay() {
		// Jakhal due 7:40, tapped 7:42 (2 minutes). Next stop due 7:55, now 7:48 (not late yet).
		Status status = morning(null, stop(1, "Sadhanwas", "07:25", "07:26"), stop(2, "Jakhal", "07:40", "07:42"),
				stop(3, "Kanheri", "07:55", null), stop(4, "Tohana town", "08:02", null));
		assertThat(status.state()).isEqualTo(RouteState.ON_THE_WAY);
		assertThat(status.lateMinutes()).isEqualTo(2);
	}

	@Test
	void nextStopAlready10MinutesOverdueMakesTheBusLate() {
		// Last tap was on time (7:25), but the next stop was due 7:30 and it is 7:48 now: 18 minutes.
		Status status = morning(null, stop(1, "A", "07:25", "07:25"), stop(2, "B", "07:30", null));
		assertThat(status.state()).isEqualTo(RouteState.LATE);
		assertThat(status.lateMinutes()).isEqualTo(18);
	}

	@Test
	void earlyTapNeverGivesNegativeLateness() {
		Status status = morning(null, stop(1, "A", "07:40", "07:30"), stop(2, "B", "07:55", null));
		assertThat(status.lateMinutes()).isZero();
		assertThat(status.state()).isEqualTo(RouteState.ON_THE_WAY);
	}

	@Test
	void reachedSchoolWinsOverEverything() {
		// Route 2: REACHED_SCHOOL at 7:46. Even a stop with no taps and an old due time does not matter.
		Status status = morning(at("07:46"), stop(1, "A", "07:00", null), stop(2, "B", "07:10", null));
		assertThat(status.state()).isEqualTo(RouteState.REACHED_SCHOOL);
		assertThat(status.reachedAt()).isEqualTo(at("07:46"));
		assertThat(status.lateMinutes()).isZero();
	}

	@Test
	void stopsAreDoneNextLater() {
		// A stop with no children (no tap) before the last tapped stop is DONE too.
		Status status = morning(null, stop(1, "Sadhanwas", "07:25", "07:26"), stop(2, "Empty stop", "07:33", null),
				stop(3, "Jakhal", "07:40", "07:42"), stop(4, "Kanheri", "07:55", null),
				stop(5, "Tohana town", "08:02", null));
		assertThat(status.stops()).extracting(BusStatusCalculator.StopStatus::state)
			.containsExactly(StopState.DONE, StopState.DONE, StopState.DONE, StopState.NEXT, StopState.LATER);
		assertThat(status.stops().get(2).tappedAt()).isEqualTo(at("07:42"));
		assertThat(status.stops().get(1).tappedAt()).isNull();
	}

	@Test
	void allStopsTappedLeavesNoNextStop() {
		Status status = morning(null, stop(1, "A", "07:25", "07:26"), stop(2, "B", "07:40", "07:41"));
		assertThat(status.stops()).extracting(BusStatusCalculator.StopStatus::state)
			.containsExactly(StopState.DONE, StopState.DONE);
		assertThat(status.state()).isEqualTo(RouteState.ON_THE_WAY);
	}

	@Test
	void stopWithoutTimeIsNotUsedForLateness() {
		StopInput noTime = new StopInput(1L, "A", null, null);
		assertThat(morning(null, noTime).state()).isEqualTo(RouteState.NOT_STARTED);
		Status status = morning(null, noTime, stop(2, "B", "07:30", "07:31"));
		assertThat(status.state()).isEqualTo(RouteState.ON_THE_WAY);
	}

	// Evening: stops are in evening order (reverse of morning).

	private static List<StopInput> homeStops(String tappedFirst) {
		return List.of(new StopInput(4L, "Tohana town", at("14:30"), (tappedFirst == null) ? null : at(tappedFirst)),
				new StopInput(3L, "Kanheri", at("14:45"), null));
	}

	@Test
	void eveningWithNoAnswersIsNotStarted() {
		Status status = BusStatusCalculator.evening(homeStops(null), 19, 0, 0, 0, null);
		assertThat(status.state()).isEqualTo(RouteState.NOT_STARTED);
		assertThat(status.phase()).isEqualTo(BusPhase.EVENING);
	}

	@Test
	void eveningWithSomeAnswersIsBoarding() {
		assertThat(BusStatusCalculator.evening(homeStops(null), 19, 10, 9, 0, null).state())
			.isEqualTo(RouteState.BOARDING);
	}

	@Test
	void eveningWhenEveryoneIsAnsweredIsOnTheWay() {
		assertThat(BusStatusCalculator.evening(homeStops(null), 19, 19, 17, 0, null).state())
			.isEqualTo(RouteState.ON_THE_WAY);
		Status firstHome = BusStatusCalculator.evening(homeStops("14:33"), 19, 19, 17, 2, at("14:33"));
		assertThat(firstHome.state()).isEqualTo(RouteState.ON_THE_WAY);
		assertThat(firstHome.stops()).extracting(BusStatusCalculator.StopStatus::state)
			.containsExactly(StopState.DONE, StopState.NEXT);
	}

	@Test
	void eveningWhenEveryBoardedChildIsHomeIsDone() {
		assertThat(BusStatusCalculator.evening(homeStops("14:33"), 19, 19, 17, 17, at("15:10")).state())
			.isEqualTo(RouteState.DONE);
	}

	@Test
	void eveningWithNobodyBoardedIsNeverDone() {
		// Everyone is absent: nothing to drop, and 0 >= 0 must not count as DONE.
		assertThat(BusStatusCalculator.evening(homeStops(null), 3, 3, 0, 0, null).state())
			.isEqualTo(RouteState.ON_THE_WAY);
	}

}

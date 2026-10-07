package com.muhjain.school.trip;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.muhjain.school.student.StudentBus;
import com.muhjain.school.student.StudentQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves taps (rules 4 to 10 of the phase file).
 * <p>
 * Story: at 7:42 the attendant taps DONE offline. At 7:43 online he corrects it to ABSENT. At 7:55 the phone
 * sends the old 7:42 tap. The row stays ABSENT, because the stored tap is newer.
 */
@Service
public class MarkService {

	/** A phone clock more than this far in the future is wrong (rule 9). */
	static final Duration CLOCK_SLACK = Duration.ofMinutes(5);

	static final String UNKNOWN_STUDENT = "UNKNOWN_STUDENT";

	static final String NOT_ON_BUS = "NOT_ON_BUS";

	static final String INVALID_OUTCOME = "INVALID_OUTCOME";

	private final BoardingEventRepository events;

	private final StudentQueryService studentQuery;

	private final TripAccess tripAccess;

	private final BoardingNotifier notifier;

	private final Clock clock;

	public MarkService(BoardingEventRepository events, StudentQueryService studentQuery, TripAccess tripAccess,
			BoardingNotifier notifier, Clock clock) {
		this.events = events;
		this.studentQuery = studentQuery;
		this.tripAccess = tripAccess;
		this.notifier = notifier;
		this.clock = clock;
	}

	/**
	 * Handles every tap alone. One bad tap does not stop the others. A bad tap saves nothing.
	 *
	 * @param userId the logged-in user, from the token
	 * @return one result per tap, in the same order
	 */
	@Transactional
	public List<MarkResult> apply(Long userId, List<MarkRequest> marks) {
		TripAccess.Access access = tripAccess.forUser(userId);
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		// The child's route comes from our own tables, per day of the tap. One query per day, not per tap.
		Map<LocalDate, Map<Long, StudentBus>> buses = new HashMap<>();
		marks.stream()
			.collect(Collectors.groupingBy(MarkRequest::serviceDate,
					Collectors.mapping(MarkRequest::studentId, Collectors.toSet())))
			.forEach((day, ids) -> buses.put(day, studentQuery.busOn(ids, day)));
		List<MarkResult> results = new ArrayList<>(marks.size());
		for (MarkRequest mark : marks) {
			StudentBus bus = buses.get(mark.serviceDate()).get(mark.studentId());
			Optional<String> error = check(access, mark, bus);
			if (error.isPresent()) {
				results.add(MarkResult.failed(mark, error.get()));
				continue;
			}
			save(userId, mark, bus.routeId(), now);
			results.add(MarkResult.ok(mark));
		}
		return results;
	}

	// Rule 5, in this order.
	private Optional<String> check(TripAccess.Access access, MarkRequest mark, StudentBus bus) {
		if (bus == null || !bus.active()) {
			return Optional.of(UNKNOWN_STUDENT);
		}
		if (bus.routeId() == null) {
			return Optional.of(NOT_ON_BUS);
		}
		Optional<String> denial = access.writeDenial(bus.routeId(), mark.serviceDate());
		if (denial.isPresent()) {
			return denial;
		}
		if (mark.outcome() == Outcome.NOT_TRAVELLING && mark.eventType() != EventType.BOARDED_EVENING) {
			return Optional.of(INVALID_OUTCOME);
		}
		return Optional.empty();
	}

	private void save(Long userId, MarkRequest mark, Long routeId, Instant now) {
		// Rule 9: a phone with a wrong clock must not put a tap in the future.
		Instant occurredAt = mark.occurredAt().isAfter(now.plus(CLOCK_SLACK)) ? now
				: mark.occurredAt().truncatedTo(ChronoUnit.MICROS);
		Optional<BoardingEvent> row = events.lockOne(mark.studentId(), mark.serviceDate(), mark.eventType());
		if (mark.outcome() == Outcome.CLEARED) {
			// Rule 8: undo deletes the row. An older undo does not delete a newer tap (rule 7).
			row.filter(r -> !r.getOccurredAt().isAfter(occurredAt)).ifPresent(events::delete);
			return;
		}
		if (row.isEmpty()) {
			int inserted = events.insertIfAbsent(mark.studentId(), routeId, mark.serviceDate(),
					mark.eventType().name(), mark.outcome().name(), occurredAt, userId, now);
			if (inserted == 1) {
				becameDone(mark, occurredAt);
				return;
			}
			// Another phone saved the same tap a moment ago. Judge ours against it.
			row = events.lockOne(mark.studentId(), mark.serviceDate(), mark.eventType());
		}
		BoardingEvent stored = row.orElseThrow();
		if (stored.getOccurredAt().isAfter(occurredAt)) {
			return; // Rule 7: the stored tap is newer. Answer ok, change nothing.
		}
		boolean wasDone = stored.getOutcome() == Outcome.DONE;
		stored.change(routeId, mark.outcome(), occurredAt, userId, now);
		if (!wasDone) {
			becameDone(mark, occurredAt);
		}
	}

	// Rule 10: only the first time a tap is DONE. Same transaction as the save.
	private void becameDone(MarkRequest mark, Instant occurredAt) {
		if (mark.outcome() == Outcome.DONE) {
			notifier.onDone(mark.studentId(), mark.eventType(), mark.serviceDate(), occurredAt);
		}
	}

}

package com.muhjain.school.trip;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardingEventRepository extends JpaRepository<BoardingEvent, Long> {

	/**
	 * The row of a tap, locked until the end of the transaction. Two phones that send the same tap at the same
	 * moment wait for each other here, so the answer of the second one is judged against the first one.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from BoardingEvent e where e.studentId = :studentId and e.serviceDate = :day "
			+ "and e.eventType = :type")
	Optional<BoardingEvent> lockOne(@Param("studentId") Long studentId, @Param("day") LocalDate day,
			@Param("type") EventType type);

	/**
	 * Inserts the row if there is none. If another transaction saved the same tap a moment ago, it does nothing
	 * and the database does not raise an error (so the transaction stays alive). Returns 1 if it inserted, 0 if not.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = false)
	@Query(value = "insert into boarding_event (student_id, route_id, service_date, event_type, outcome, "
			+ "occurred_at, recorded_by, recorded_at) values (:studentId, :routeId, :day, :type, :outcome, "
			+ ":occurredAt, :recordedBy, :recordedAt) on conflict (student_id, service_date, event_type) do nothing",
			nativeQuery = true)
	int insertIfAbsent(@Param("studentId") Long studentId, @Param("routeId") Long routeId,
			@Param("day") LocalDate day, @Param("type") String type, @Param("outcome") String outcome,
			@Param("occurredAt") Instant occurredAt, @Param("recordedBy") Long recordedBy,
			@Param("recordedAt") Instant recordedAt);

	/** Every tap of one route on one day. */
	List<BoardingEvent> findByRouteIdAndServiceDate(Long routeId, LocalDate day);

	/** Every tap of the school on one day. */
	List<BoardingEvent> findByServiceDate(LocalDate day);

	/** The taps of some children on one day (the child may have changed route in between, so not by route). */
	List<BoardingEvent> findByStudentIdInAndServiceDate(Collection<Long> studentIds, LocalDate day);

}

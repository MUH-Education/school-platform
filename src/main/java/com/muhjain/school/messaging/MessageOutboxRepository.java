package com.muhjain.school.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageOutboxRepository extends JpaRepository<MessageOutbox, Long> {

	/**
	 * Saves one boarding SMS row unless this phone already has one for the same child, day and event (the partial
	 * unique index). It never raises an error, so the transaction of the tap stays alive. 1 = saved, 0 = existed.
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "insert into message_outbox (purpose, channel, phone, guardian_id, student_id, service_date, "
			+ "event_type, template_code, body, status, created_at, updated_at) values ('BOARDING', 'SMS', :phone, :guardianId, :studentId, "
			+ ":day, :eventType, :templateCode, :body, 'QUEUED', :now, :now) on conflict (guardian_id, student_id, "
			+ "service_date, event_type) where purpose = 'BOARDING' do nothing", nativeQuery = true)
	int queueBoarding(@Param("phone") String phone, @Param("guardianId") Long guardianId,
			@Param("studentId") Long studentId, @Param("day") LocalDate day, @Param("eventType") String eventType,
			@Param("templateCode") String templateCode, @Param("body") String body, @Param("now") Instant now);

	/**
	 * The next QUEUED rows, oldest first, locked for this transaction. Rows locked by another worker are skipped,
	 * so two workers never send the same row.
	 */
	@Query(value = "select * from message_outbox where status = 'QUEUED' order by id limit :limit "
			+ "for update skip locked", nativeQuery = true)
	List<MessageOutbox> claimQueued(@Param("limit") int limit);

	/** The messages of a time range, newest first. Every filter is optional (null = any). */
	@Query("select m from MessageOutbox m where m.createdAt >= :from and m.createdAt < :to "
			+ "and (:status is null or m.status = :status) and (:studentId is null or m.studentId = :studentId) "
			+ "and (:phoneLike is null or m.phone like :phoneLike) order by m.id desc")
	Page<MessageOutbox> search(@Param("from") Instant from, @Param("to") Instant to,
			@Param("status") MessageStatus status, @Param("studentId") Long studentId,
			@Param("phoneLike") String phoneLike, Pageable pageable);

	/** Each row is {status, count} for the range. */
	@Query("select m.status, count(m) from MessageOutbox m where m.createdAt >= :from and m.createdAt < :to "
			+ "group by m.status")
	List<Object[]> countByStatus(@Param("from") Instant from, @Param("to") Instant to);

	/** The boarding messages of some children on one day. Bus status shows them per child and event. */
	@Query("select m from MessageOutbox m where m.purpose = com.muhjain.school.messaging.MessagePurpose.BOARDING "
			+ "and m.serviceDate = :day and m.studentId in :studentIds")
	List<MessageOutbox> boardingFor(@Param("studentIds") Collection<Long> studentIds, @Param("day") LocalDate day);

}

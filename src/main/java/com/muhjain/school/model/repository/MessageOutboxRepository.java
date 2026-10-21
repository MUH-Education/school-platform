package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.enums.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface MessageOutboxRepository extends JpaRepository<MessageOutbox, Long> {

	/**
	 * Saves one boarding SMS row unless this phone already has one for the same child, day and event (the partial
	 * unique index). It never raises an error, so the transaction of the tap stays alive. 1 = saved, 0 = existed.
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "insert into message_outbox (purpose, channel, phone, guardian_id, student_id, service_date, "
			+ "event_type, template_code, body, status, next_try_at, created_at, updated_at) values ('BOARDING', "
			+ "'SMS', :phone, :guardianId, :studentId, :day, :eventType, :templateCode, :body, 'QUEUED', :now, :now, "
			+ ":now) on conflict (guardian_id, student_id, service_date, event_type) "
			+ "where purpose = 'BOARDING' and fallback_of_id is null do nothing", nativeQuery = true)
	int queueBoarding(@Param("phone") String phone, @Param("guardianId") Long guardianId,
			@Param("studentId") Long studentId, @Param("day") LocalDate day, @Param("eventType") String eventType,
			@Param("templateCode") String templateCode, @Param("body") String body, @Param("now") Instant now);

	/**
	 * The next QUEUED rows, oldest first, locked for this transaction. Rows locked by another worker are skipped,
	 * so two workers never send the same row.
	 */
	@Query(value = "select * from message_outbox where status = 'QUEUED' and next_try_at <= :now order by id "
			+ "limit :limit for update skip locked", nativeQuery = true)
	List<MessageOutbox> claimQueued(@Param("now") Instant now, @Param("limit") int limit);

	/** Cancel while sending: the rows not sent yet become CANCELLED; rows already sent stay. Returns how many. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update MessageOutbox m set m.status = com.muhjain.school.model.enums.MessageStatus.CANCELLED, "
			+ "m.updatedAt = :now where m.notificationId = :notificationId "
			+ "and m.status = com.muhjain.school.model.enums.MessageStatus.QUEUED")
	int cancelQueued(@Param("notificationId") Long notificationId, @Param("now") Instant now);

	/** How many rows of a notification are in these statuses (the test copy not counted). */
	@Query("select count(m) from MessageOutbox m where m.notificationId = :notificationId and m.status in :statuses "
			+ "and m.purpose <> com.muhjain.school.model.enums.MessagePurpose.TEST")
	long countOfNotification(@Param("notificationId") Long notificationId,
			@Param("statuses") Collection<MessageStatus> statuses);

	/** The rows of a notification, in the order they were written (the test copy not shown). */
	@Query("select m from MessageOutbox m where m.notificationId = :notificationId "
			+ "and m.purpose <> com.muhjain.school.model.enums.MessagePurpose.TEST order by m.id")
	Page<MessageOutbox> pageOfNotification(@Param("notificationId") Long notificationId, Pageable pageable);

	/** The phones that already have a row of this notification (the test copy not counted). */
	@Query("select m.phone from MessageOutbox m where m.notificationId = :notificationId "
			+ "and m.purpose <> com.muhjain.school.model.enums.MessagePurpose.TEST")
	List<String> phonesOfNotification(@Param("notificationId") Long notificationId);

	/**
	 * How many messages of this purpose went (or will go) to this phone since a moment, from other notifications.
	 * Example: MARKETING to +919811100001 in the last 7 days → 2 (the weekly cap).
	 */
	long countByPhoneAndPurposeAndStatusInAndCreatedAtGreaterThanEqualAndNotificationIdNot(String phone,
			MessagePurpose purpose, Collection<MessageStatus> statuses, Instant since, Long notificationId);

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
	@Query("select m from MessageOutbox m where m.purpose = com.muhjain.school.model.enums.MessagePurpose.BOARDING "
			+ "and m.serviceDate = :day and m.studentId in :studentIds")
	List<MessageOutbox> boardingFor(@Param("studentIds") Collection<Long> studentIds, @Param("day") LocalDate day);

}

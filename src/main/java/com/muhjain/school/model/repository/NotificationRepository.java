package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.NotificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/**
	 * The notification, locked until the transaction ends. Cancel uses it, so it waits for a dispatcher that is
	 * expanding the same notification instead of cancelling half of it.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select n from Notification n where n.id = :id")
	Optional<Notification> findLockedById(@Param("id") Long id);

	/** FAILED with a note, by plain SQL: works even when the row cannot be loaded as an entity. */
	@org.springframework.data.jpa.repository.Modifying
	@Query(value = "update notification set status = 'FAILED', note = :note, finished_at = :now, updated_at = :now "
			+ "where id = :id", nativeQuery = true)
	int markFailed(@Param("id") Long id, @Param("note") String note, @Param("now") Instant now);

	/** The Home and Reports list, newest first. {@code kind} and {@code status} null = any. */
	@Query("select n from Notification n where (:kind is null or n.kind = :kind) "
			+ "and (:status is null or n.status = :status) and n.createdAt >= :from and n.createdAt < :to "
			+ "order by n.id desc")
	Page<Notification> search(@Param("kind") NotificationKind kind, @Param("status") NotificationStatus status,
			@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

	/**
	 * SCHEDULED notifications whose time has come, oldest first, locked for this transaction. A row locked by
	 * another app instance is skipped, so two dispatchers never expand the same notification.
	 * {@code now} comes from the app clock, not the database clock, so a test can move time.
	 */
	@Query(value = "select * from notification where status = 'SCHEDULED' and scheduled_at <= :now "
			+ "order by scheduled_at, id limit :limit for update skip locked", nativeQuery = true)
	List<Notification> claimDue(@Param("now") Instant now, @Param("limit") int limit);

	/**
	 * SENDING notifications whose outbox rows are not written yet ("Send now", or a crash in the middle), locked
	 * the same way as {@link #claimDue}.
	 */
	@Query(value = "select * from notification where status = 'SENDING' and expanded_at is null "
			+ "order by id limit :limit for update skip locked", nativeQuery = true)
	List<Notification> findSendingWithoutExpansion(@Param("limit") int limit);

	/**
	 * SENDING notifications whose rows are written and that have no QUEUED row left: ready to close. Locked like
	 * {@link #claimDue}, so a cancel and a close never meet.
	 */
	@Query(value = "select * from notification n where n.status = 'SENDING' and n.expanded_at is not null "
			+ "and not exists (select 1 from message_outbox m where m.notification_id = n.id and m.status = 'QUEUED' "
			+ "and m.purpose <> 'TEST') order by n.id limit :limit for update skip locked", nativeQuery = true)
	List<Notification> claimFinished(@Param("limit") int limit);

	/**
	 * Per rule: how many notifications it made since {@code from}, and to how many people (counted at send time).
	 * Rows are {@code [ruleId, notifications, recipients]}. Feeds the Settings table.
	 */
	@Query("select n.ruleId, count(n), coalesce(sum(n.recipientCount), 0) from Notification n "
			+ "where n.ruleId is not null and n.createdAt >= :from group by n.ruleId")
	List<Object[]> countByRuleSince(@Param("from") Instant from);

}

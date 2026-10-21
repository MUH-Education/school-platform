package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

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

}

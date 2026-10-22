package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.NotificationRuleRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface RuleRunRepository extends JpaRepository<NotificationRuleRun, NotificationRuleRun.Key> {

	/**
	 * Saves the run, or does nothing when this rule already ran with this key. 1 = this caller won and must fire,
	 * 0 = someone fired it already (the scheduler running twice, a restart, a second app instance).
	 * Use {@link #tryInsert} for the yes/no answer.
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "insert into notification_rule_run (rule_id, run_key, ran_at) values (:ruleId, :runKey, :ranAt) "
			+ "on conflict (rule_id, run_key) do nothing", nativeQuery = true)
	int insertIfAbsent(@Param("ruleId") Long ruleId, @Param("runKey") String runKey, @Param("ranAt") Instant ranAt);

	/** True when this call saved the run (fire now), false when the run was there already (do nothing). */
	default boolean tryInsert(Long ruleId, String runKey, Instant ranAt) {
		return insertIfAbsent(ruleId, runKey, ranAt) == 1;
	}

	/** Writes the notification the run made. Called in the same transaction, after it is saved. */
	@Modifying(flushAutomatically = true)
	@Query(value = "update notification_rule_run set notification_id = :notificationId "
			+ "where rule_id = :ruleId and run_key = :runKey", nativeQuery = true)
	int linkNotification(@Param("ruleId") Long ruleId, @Param("runKey") String runKey,
			@Param("notificationId") Long notificationId);

	/** The newest run of a rule (for "last run" on the Settings screen). */
	java.util.Optional<NotificationRuleRun> findFirstByIdRuleIdOrderByRanAtDesc(Long ruleId);

}

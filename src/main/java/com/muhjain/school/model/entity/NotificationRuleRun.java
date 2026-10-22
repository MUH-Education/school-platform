package com.muhjain.school.model.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * One run of a rule. The key (rule, run key) is what stops the same run from happening twice, even after a restart.
 * Rows are written only by {@code RuleRunRepository.tryInsert}, never by {@code save}.
 * Example: rule 1, {@code 2026-10-10}, notification 57. Run keys: CRON (BIRTHDAY too) {@code 2026-10-10}, EVENT
 * {@code student:129}, "Run now" {@code manual:<timestamp>}.
 */
@Entity
@Table(name = "notification_rule_run")
public class NotificationRuleRun {

	@EmbeddedId
	private Key id;

	// Null when the run found nobody to send to.
	@Column(name = "notification_id")
	private Long notificationId;

	@Column(name = "ran_at", nullable = false)
	private Instant ranAt;

	protected NotificationRuleRun() {
	}

	public Key getId() {
		return id;
	}

	public Long getNotificationId() {
		return notificationId;
	}

	public Instant getRanAt() {
		return ranAt;
	}

	/** The two key columns. */
	@Embeddable
	public static class Key implements Serializable {

		@Column(name = "rule_id", nullable = false)
		private Long ruleId;

		@Column(name = "run_key", nullable = false, length = 60)
		private String runKey;

		protected Key() {
		}

		public Key(Long ruleId, String runKey) {
			this.ruleId = ruleId;
			this.runKey = runKey;
		}

		public Long getRuleId() {
			return ruleId;
		}

		public String getRunKey() {
			return runKey;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Key key && Objects.equals(ruleId, key.ruleId) && Objects.equals(runKey, key.runKey);
		}

		@Override
		public int hashCode() {
			return Objects.hash(ruleId, runKey);
		}

	}

}

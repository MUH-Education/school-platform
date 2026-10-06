package com.muhjain.school.audit;

import java.time.Instant;
import java.util.Map;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One line of "who changed what".
 * Example: USER 2, UPDATED, "Role changed from ADMISSIONS_DESK to OFFICE_ADMIN", changed by user 1.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog extends BaseEntity {

	@Column(name = "entity_type", nullable = false, length = 40)
	private String entityType;

	@Column(name = "entity_id", nullable = false)
	private Long entityId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AuditAction action;

	@Column(nullable = false, length = 300)
	private String summary;

	// Old and new values. Example: {"role": {"old": "ADMISSIONS_DESK", "new": "OFFICE_ADMIN"}}
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> details;

	@Column(name = "changed_by")
	private Long changedBy;

	@Column(name = "changed_at", nullable = false)
	private Instant changedAt;

	protected AuditLog() {
	}

	public AuditLog(String entityType, Long entityId, AuditAction action, String summary, Map<String, Object> details,
			Long changedBy, Instant changedAt) {
		this.entityType = entityType;
		this.entityId = entityId;
		this.action = action;
		this.summary = summary;
		this.details = details;
		this.changedBy = changedBy;
		this.changedAt = changedAt;
	}

	public String getEntityType() {
		return entityType;
	}

	public Long getEntityId() {
		return entityId;
	}

	public AuditAction getAction() {
		return action;
	}

	public String getSummary() {
		return summary;
	}

	public Map<String, Object> getDetails() {
		return details;
	}

	public Long getChangedBy() {
		return changedBy;
	}

	public Instant getChangedAt() {
		return changedAt;
	}

}

package com.muhjain.school.model.entity;

import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.RuleEvent;
import com.muhjain.school.model.enums.RuleTrigger;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * One automatic message: a line of the Settings screen's "Automatic messages" table. At the right moment it creates
 * a normal notification (source RULE); it never sends anything by itself.
 * Example: FEE_REMINDER_10TH, kind FEE_REMINDER, CRON {@code 0 0 10 10 * *}, audience FAMILIES_WITH_DUES, template 41,
 * on. The eight rows are seeded by V15 (all off, no template) and never deleted.
 */
@Entity
@Table(name = "notification_rule")
public class NotificationRule extends BaseEntity {

	@Column(nullable = false, unique = true, updatable = false, length = 40)
	private String code;

	// The sender on Home: "Automatic · Fee reminder 10th".
	@Column(nullable = false, length = 80)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationKind kind;

	// Empty until the owner picks one; a rule without a template cannot be on.
	@Column(name = "template_id")
	private Long templateId;

	// Same shape as notification.audience. The family (WELCOME) or the route (BUS_LATE) is added at fire time.
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Audience audience;

	@Enumerated(EnumType.STRING)
	@Column(name = "trigger_type", nullable = false, length = 10)
	private RuleTrigger triggerType;

	// Spring cron, school time (Asia/Kolkata). Only for CRON.
	@Column(length = 60)
	private String cron;

	// Only for EVENT.
	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private RuleEvent event;

	// FESTIVAL: {"dates": ["2027-03-04"], "Festival": {"2027-03-04": "होली"}}. Others: {}.
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "fixed_values", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> fixedValues = new HashMap<>();

	@Column(name = "delay_minutes")
	private Integer delayMinutes;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "last_run_at")
	private Instant lastRunAt;

	@Column(name = "updated_by")
	private Long updatedBy;

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public NotificationKind getKind() {
		return kind;
	}

	public Long getTemplateId() {
		return templateId;
	}

	public void setTemplateId(Long templateId) {
		this.templateId = templateId;
	}

	public Audience getAudience() {
		return audience;
	}

	public void setAudience(Audience audience) {
		this.audience = audience;
	}

	public RuleTrigger getTriggerType() {
		return triggerType;
	}

	public String getCron() {
		return cron;
	}

	public void setCron(String cron) {
		this.cron = cron;
	}

	public RuleEvent getEvent() {
		return event;
	}

	public Map<String, Object> getFixedValues() {
		return fixedValues;
	}

	public void setFixedValues(Map<String, Object> fixedValues) {
		this.fixedValues = (fixedValues == null) ? new HashMap<>() : new HashMap<>(fixedValues);
	}

	public Integer getDelayMinutes() {
		return delayMinutes;
	}

	public void setDelayMinutes(Integer delayMinutes) {
		this.delayMinutes = delayMinutes;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Instant getLastRunAt() {
		return lastRunAt;
	}

	public void setLastRunAt(Instant lastRunAt) {
		this.lastRunAt = lastRunAt;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(Long updatedBy) {
		this.updatedBy = updatedBy;
	}

}

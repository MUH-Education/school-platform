package com.muhjain.school.model.entity;

import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.enums.MediaFileType;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.NotificationSource;
import com.muhjain.school.model.enums.NotificationStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * One send by the office. The people are {@code message_outbox} rows with this {@code notification_id}, written
 * by the dispatcher at send time, never by the request.
 * Example: MARKETING "Open day, Sunday 25 October", template 31, audience CURRENT_PARENTS, SCHEDULED for
 * 24 Oct 10:00.
 */
@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationKind kind;

	// Office-only name, never sent.
	@Column(nullable = false, length = 80)
	private String name;

	@Column(name = "template_id", nullable = false)
	private Long templateId;

	// The text with {boxes}, as the office saw it.
	@Column(nullable = false, length = 1024)
	private String body;

	// SMS version, null = no SMS fallback.
	@Column(name = "sms_body", length = 320)
	private String smsBody;

	// Values that are the same for everyone. Example: {"Date": "10 अक्टूबर"}
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "fixed_values", nullable = false, columnDefinition = "jsonb")
	private Map<String, String> fixedValues = new HashMap<>();

	@Column(name = "media_key", length = 120)
	private String mediaKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "media_type", length = 10)
	private MediaFileType mediaType;

	// Filled by MediaPublisher at send time; uploaded again after 25 days (the provider keeps it 30).
	@Column(name = "provider_media_id", length = 120)
	private String providerMediaId;

	@Column(name = "provider_media_at")
	private Instant providerMediaAt;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Audience audience;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private NotificationStatus status = NotificationStatus.DRAFT;

	@Column(name = "scheduled_at")
	private Instant scheduledAt;

	// When the outbox rows were written. SENDING with this empty = the dispatcher still has to do it.
	@Column(name = "expanded_at")
	private Instant expandedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "recipient_count")
	private Integer recipientCount;

	@Column(length = 300)
	private String note;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private NotificationSource source = NotificationSource.MANUAL;

	@Column(name = "rule_id")
	private Long ruleId;

	@Column(name = "created_by", nullable = false, updatable = false)
	private Long createdBy;

	public NotificationKind getKind() {
		return kind;
	}

	public void setKind(NotificationKind kind) {
		this.kind = kind;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Long getTemplateId() {
		return templateId;
	}

	public void setTemplateId(Long templateId) {
		this.templateId = templateId;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getSmsBody() {
		return smsBody;
	}

	public void setSmsBody(String smsBody) {
		this.smsBody = smsBody;
	}

	public Map<String, String> getFixedValues() {
		return fixedValues;
	}

	public void setFixedValues(Map<String, String> fixedValues) {
		this.fixedValues = (fixedValues == null) ? new HashMap<>() : new HashMap<>(fixedValues);
	}

	public String getMediaKey() {
		return mediaKey;
	}

	public void setMediaKey(String mediaKey) {
		this.mediaKey = mediaKey;
	}

	public MediaFileType getMediaType() {
		return mediaType;
	}

	public void setMediaType(MediaFileType mediaType) {
		this.mediaType = mediaType;
	}

	public String getProviderMediaId() {
		return providerMediaId;
	}

	public void setProviderMediaId(String providerMediaId) {
		this.providerMediaId = providerMediaId;
	}

	public Instant getProviderMediaAt() {
		return providerMediaAt;
	}

	public void setProviderMediaAt(Instant providerMediaAt) {
		this.providerMediaAt = providerMediaAt;
	}

	public Audience getAudience() {
		return audience;
	}

	public void setAudience(Audience audience) {
		this.audience = audience;
	}

	public NotificationStatus getStatus() {
		return status;
	}

	public void setStatus(NotificationStatus status) {
		this.status = status;
	}

	public Instant getScheduledAt() {
		return scheduledAt;
	}

	public void setScheduledAt(Instant scheduledAt) {
		this.scheduledAt = scheduledAt;
	}

	public Instant getExpandedAt() {
		return expandedAt;
	}

	public void setExpandedAt(Instant expandedAt) {
		this.expandedAt = expandedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public void setFinishedAt(Instant finishedAt) {
		this.finishedAt = finishedAt;
	}

	public Integer getRecipientCount() {
		return recipientCount;
	}

	public void setRecipientCount(Integer recipientCount) {
		this.recipientCount = recipientCount;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public NotificationSource getSource() {
		return source;
	}

	public void setSource(NotificationSource source) {
		this.source = source;
	}

	public Long getRuleId() {
		return ruleId;
	}

	public void setRuleId(Long ruleId) {
		this.ruleId = ruleId;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(Long createdBy) {
		this.createdBy = createdBy;
	}

}

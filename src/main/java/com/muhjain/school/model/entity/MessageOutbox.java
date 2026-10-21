package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * One message to one phone. Example: BOARDING, SMS, +919811100001, Aryan, 7 Oct 2026, BOARDED_MORNING, QUEUED.
 * Other features are ids, not objects. {@code templateCode} has no foreign key any more (bus texts now have a
 * boy row and a girl row for the same code), it is only kept for reading.
 * <p>
 * A notification (Phase 11) makes one row per person and channel, with {@code notificationId} set. Example:
 * NOTICE, WHATSAPP, +919811100001, notification 17, values {"Parent name": "Rajesh Punia"}, QUEUED.
 */
@Entity
@Table(name = "message_outbox")
public class MessageOutbox extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MessagePurpose purpose;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MessageChannel channel;

	@Column(nullable = false, length = 13)
	private String phone;

	@Column(name = "guardian_id")
	private Long guardianId;

	@Column(name = "student_id")
	private Long studentId;

	@Column(name = "receipt_id")
	private Long receiptId;

	@Column(name = "service_date")
	private LocalDate serviceDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", length = 20)
	private EventType eventType;

	@Column(name = "template_code", length = 40)
	private String templateCode;

	@Column(nullable = false, length = 1024)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private MessageStatus status = MessageStatus.QUEUED;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "next_try_at", nullable = false)
	private Instant nextTryAt;

	@Column(name = "provider_ref", length = 120)
	private String providerRef;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private MessageProblem problem;

	@Column(length = 300)
	private String error;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "delivered_at")
	private Instant deliveredAt;

	// This SMS went because the WhatsApp message it follows failed.
	@Column(name = "fallback_of_id")
	private Long fallbackOfId;

	// Empty = made by the system (a tap), not typed by someone.
	@Column(name = "created_by")
	private Long createdBy;

	@Column(name = "notification_id")
	private Long notificationId;

	// A STAFF recipient of a notification.
	@Column(name = "staff_id")
	private Long staffId;

	// An ENQUIRY recipient of a notification.
	@Column(name = "enquiry_id")
	private Long enquiryId;

	@Column(name = "read_at")
	private Instant readAt;

	// The last button this person tapped.
	@Enumerated(EnumType.STRING)
	@Column(name = "button_action", length = 30)
	private ButtonAction buttonAction;

	// Null for text only.
	@Column(name = "media_key", length = 120)
	private String mediaKey;

	// The {box} values of this one person, so the row can be sent (again) on its own.
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "template_values", nullable = false, columnDefinition = "jsonb")
	private Map<String, String> templateValues = new HashMap<>();

	// Why a guard said no. Set only with status SKIPPED.
	@Enumerated(EnumType.STRING)
	@Column(name = "skip_reason", length = 30)
	private SkipReason skipReason;

	protected MessageOutbox() {
	}

	/**
	 * A new QUEUED row, ready to send from {@code now}. Used for notification rows; bus SMS are still saved with
	 * the one-statement insert of {@code MessageOutboxRepository.queueBoarding}.
	 */
	public MessageOutbox(MessagePurpose purpose, MessageChannel channel, String phone, String body, Instant now) {
		this.purpose = purpose;
		this.channel = channel;
		this.phone = phone;
		this.body = body;
		this.nextTryAt = now;
	}

	public MessagePurpose getPurpose() {
		return purpose;
	}

	public MessageChannel getChannel() {
		return channel;
	}

	public String getPhone() {
		return phone;
	}

	public Long getGuardianId() {
		return guardianId;
	}

	public Long getStudentId() {
		return studentId;
	}

	public Long getReceiptId() {
		return receiptId;
	}

	public LocalDate getServiceDate() {
		return serviceDate;
	}

	public EventType getEventType() {
		return eventType;
	}

	public String getTemplateCode() {
		return templateCode;
	}

	public String getBody() {
		return body;
	}

	public MessageStatus getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public Instant getNextTryAt() {
		return nextTryAt;
	}

	public String getProviderRef() {
		return providerRef;
	}

	public MessageProblem getProblem() {
		return problem;
	}

	public String getError() {
		return error;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public Instant getDeliveredAt() {
		return deliveredAt;
	}

	public Long getFallbackOfId() {
		return fallbackOfId;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(Long createdBy) {
		this.createdBy = createdBy;
	}

	public void setGuardianId(Long guardianId) {
		this.guardianId = guardianId;
	}

	public void setStudentId(Long studentId) {
		this.studentId = studentId;
	}

	public void setTemplateCode(String templateCode) {
		this.templateCode = templateCode;
	}

	public void setFallbackOfId(Long fallbackOfId) {
		this.fallbackOfId = fallbackOfId;
	}

	/** Do not try before this time. Example: quiet hours hold a 10 pm row until 8 am. */
	public void setNextTryAt(Instant nextTryAt) {
		this.nextTryAt = nextTryAt;
	}

	public Long getNotificationId() {
		return notificationId;
	}

	public void setNotificationId(Long notificationId) {
		this.notificationId = notificationId;
	}

	public Long getStaffId() {
		return staffId;
	}

	public void setStaffId(Long staffId) {
		this.staffId = staffId;
	}

	public Long getEnquiryId() {
		return enquiryId;
	}

	public void setEnquiryId(Long enquiryId) {
		this.enquiryId = enquiryId;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public void setReadAt(Instant readAt) {
		this.readAt = readAt;
	}

	public ButtonAction getButtonAction() {
		return buttonAction;
	}

	public void setButtonAction(ButtonAction buttonAction) {
		this.buttonAction = buttonAction;
	}

	public String getMediaKey() {
		return mediaKey;
	}

	public void setMediaKey(String mediaKey) {
		this.mediaKey = mediaKey;
	}

	public Map<String, String> getTemplateValues() {
		return templateValues;
	}

	public void setTemplateValues(Map<String, String> templateValues) {
		this.templateValues = (templateValues == null) ? new HashMap<>() : new HashMap<>(templateValues);
	}

	public SkipReason getSkipReason() {
		return skipReason;
	}

	/** A guard said no. The worker never picks a SKIPPED row; the report shows the reason. */
	public void markSkipped(SkipReason reason) {
		this.status = MessageStatus.SKIPPED;
		this.skipReason = reason;
	}

	/** The provider took it (or test mode logged it). */
	public void markDone(MessageStatus status, String providerRef, Instant now) {
		this.status = status;
		this.providerRef = providerRef;
		this.sentAt = now;
		this.error = null;
		this.problem = null;
	}

	/** A send failed. After {@code maxAttempts} failures the row is FAILED, before that it stays QUEUED. */
	public void markAttemptFailed(String error, int maxAttempts) {
		this.attempts++;
		this.error = truncate(error);
		if (this.attempts >= maxAttempts) {
			this.status = MessageStatus.FAILED;
			this.problem = MessageProblem.PROVIDER_ERROR;
		}
	}

	/** Not sent at all, for example "too old". */
	public void markFailed(String error) {
		this.status = MessageStatus.FAILED;
		this.error = truncate(error);
	}

	private static String truncate(String text) {
		if (text == null) {
			return null;
		}
		return (text.length() <= 300) ? text : text.substring(0, 300);
	}

}

package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.*;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One message to one phone. Example: BOARDING, SMS, +919811100001, Aryan, 7 Oct 2026, BOARDED_MORNING, QUEUED.
 * Other features are ids, not objects. {@code templateCode} has no foreign key any more (bus texts now have a
 * boy row and a girl row for the same code), it is only kept for reading.
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

	@Column(nullable = false, length = 1000)
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

	protected MessageOutbox() {
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

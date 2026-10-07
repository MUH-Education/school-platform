package com.muhjain.school.messaging;

import java.time.Instant;
import java.time.LocalDate;

import com.muhjain.school.common.BaseEntity;
import com.muhjain.school.trip.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One message to one phone. Example: BOARDING, SMS, +919811100001, Aryan, 7 Oct 2026, BOARDED_MORNING, QUEUED.
 * Other features are ids, not objects.
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

	@Column(name = "service_date")
	private LocalDate serviceDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", length = 20)
	private EventType eventType;

	@Column(name = "template_code", length = 40)
	private String templateCode;

	@Column(nullable = false, length = 500)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private MessageStatus status = MessageStatus.QUEUED;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "provider_ref", length = 80)
	private String providerRef;

	@Column(length = 300)
	private String error;

	@Column(name = "sent_at")
	private Instant sentAt;

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

	public String getProviderRef() {
		return providerRef;
	}

	public String getError() {
		return error;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	/** The provider took it (or test mode logged it). */
	public void markDone(MessageStatus status, String providerRef, Instant now) {
		this.status = status;
		this.providerRef = providerRef;
		this.sentAt = now;
		this.error = null;
	}

	/** A send failed. After {@code maxAttempts} failures the row is FAILED, before that it stays QUEUED. */
	public void markAttemptFailed(String error, int maxAttempts) {
		this.attempts++;
		this.error = truncate(error);
		if (this.attempts >= maxAttempts) {
			this.status = MessageStatus.FAILED;
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

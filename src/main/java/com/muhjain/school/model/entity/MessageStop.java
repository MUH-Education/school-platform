package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.StopReason;
import jakarta.persistence.*;

/**
 * A family that said "stop" to MARKETING and GREETING. They still get notices, urgent messages, fee reminders,
 * bus SMS and receipts. Example: guardian 210, WROTE_STOP, after notification 17. Taking the family off the list
 * deletes the row.
 */
@Entity
@Table(name = "message_stop")
public class MessageStop extends BaseEntity {

	@Column(name = "guardian_id", nullable = false, unique = true)
	private Long guardianId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StopReason reason;

	// The message they answered, if known.
	@Column(name = "notification_id")
	private Long notificationId;

	public Long getGuardianId() {
		return guardianId;
	}

	public void setGuardianId(Long guardianId) {
		this.guardianId = guardianId;
	}

	public StopReason getReason() {
		return reason;
	}

	public void setReason(StopReason reason) {
		this.reason = reason;
	}

	public Long getNotificationId() {
		return notificationId;
	}

	public void setNotificationId(Long notificationId) {
		this.notificationId = notificationId;
	}

}

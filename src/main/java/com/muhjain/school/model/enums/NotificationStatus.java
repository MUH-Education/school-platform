package com.muhjain.school.model.enums;

/**
 * DRAFT → SCHEDULED (send later) or SENDING (send now) → SENT. CANCELLED from DRAFT, SCHEDULED or SENDING.
 * FAILED when every row failed. "Send now" only sets SENDING; the dispatcher writes the outbox rows.
 */
public enum NotificationStatus {

	DRAFT, SCHEDULED, SENDING, SENT, CANCELLED, FAILED

}

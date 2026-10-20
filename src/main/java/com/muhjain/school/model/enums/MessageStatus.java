package com.muhjain.school.model.enums;

/**
 * QUEUED waits for the worker. SENT: the provider accepted it. DELIVERED: the provider confirmed the phone got
 * it. FAILED: tried 3 times or too old. CANCELLED: the receipt it was about was cancelled. TEST_ONLY: written to
 * the log in test mode, nothing left the server.
 */
public enum MessageStatus {

	QUEUED, SENT, DELIVERED, FAILED, CANCELLED, TEST_ONLY

}

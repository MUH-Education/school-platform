package com.muhjain.school.messaging;

/** QUEUED waits for the worker. SENT: the provider accepted it. FAILED: tried 3 times or too old. TEST_ONLY: written to the log in test mode, nothing left the server. */
public enum MessageStatus {

	QUEUED, SENT, FAILED, TEST_ONLY

}

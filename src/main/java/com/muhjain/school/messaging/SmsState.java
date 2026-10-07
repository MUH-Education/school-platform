package com.muhjain.school.messaging;

/**
 * The SMS of one child and event, as the office sees it on "One bus".
 * SENT / QUEUED / FAILED / TEST_ONLY come from the queue. NOT_FOR_CLASS: the class rule says this class gets no SMS
 * for this event. NONE: no SMS (no tap yet, absent, or no parent phone with SMS on).
 */
public enum SmsState {

	SENT, QUEUED, FAILED, TEST_ONLY, NOT_FOR_CLASS, NONE

}

package com.muhjain.school.exception;

/** The provider did not take the SMS. The message is saved in {@code message_outbox.error}, so no secrets in it. */
public class SmsSendException extends Exception {

	public SmsSendException(String message) {
		super(message);
	}

	public SmsSendException(String message, Throwable cause) {
		super(message, cause);
	}

}

package com.muhjain.school.auth;

/** A channel could not send the code. Example: the WhatsApp provider answered 500. */
public class OtpSendException extends Exception {

	public OtpSendException(String message) {
		super(message);
	}

	public OtpSendException(String message, Throwable cause) {
		super(message, cause);
	}

}

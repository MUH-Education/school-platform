package com.muhjain.school.exception;

import com.muhjain.school.model.enums.MessageProblem;

/**
 * The provider did not take the message. The text is saved in {@code message_outbox.error}, so no secrets in it.
 * {@code problem} says why when the provider tells us. Example: NOT_ON_WHATSAPP → the worker sends an SMS instead.
 */
public class SmsSendException extends Exception {

	private final MessageProblem problem;

	public SmsSendException(String message) {
		this(message, (MessageProblem) null);
	}

	public SmsSendException(String message, MessageProblem problem) {
		super(message);
		this.problem = problem;
	}

	public SmsSendException(String message, Throwable cause) {
		super(message, cause);
		this.problem = null;
	}

	/** Why it failed, or null when the provider did not say. */
	public MessageProblem problem() {
		return problem;
	}

}

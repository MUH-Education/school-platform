package com.muhjain.school.exception;

/**
 * A notification's file could not be given to the WhatsApp provider. The dispatcher leaves the notification as it
 * is and tries again the next minute; nothing is sent without its file.
 */
public class MediaPublishException extends RuntimeException {

	public MediaPublishException(String message, Throwable cause) {
		super(message, cause);
	}

}

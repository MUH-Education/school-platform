package com.muhjain.school.service.notification;

import com.muhjain.school.exception.SmsSendException;
import com.muhjain.school.util.MediaRules;

/**
 * Gives a notification's file to the WhatsApp provider (docs/10-notifications.md section 6). The file lives in our
 * own {@code FileStorage}; the provider gets a copy only at send time and answers an id that the messages point at.
 * {@code NotificationMediaService.ensurePublished} uploads once per notification and again after 25 days (the
 * provider keeps a file about 30). Example: "notifications/9b1e.mp4" → "1045738291".
 */
public interface MediaPublisher {

	/** @throws SmsSendException when the provider did not take it (the notification waits for the next minute) */
	String publish(String mediaKey, MediaRules.Format format) throws SmsSendException;

}

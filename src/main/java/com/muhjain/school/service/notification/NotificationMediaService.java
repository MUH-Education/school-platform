package com.muhjain.school.service.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.exception.MediaPublishException;
import com.muhjain.school.exception.SmsSendException;
import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.dto.response.NotificationMediaResponse;
import com.muhjain.school.model.enums.MediaFileType;
import com.muhjain.school.service.storage.FileStorage;
import com.muhjain.school.util.MediaRules;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Saves a poster, video, PDF or audio file for a notification in the school's own storage (rule 10). The provider
 * gets it only at send time ({@code MediaPublisher}, task 11.11). No database row: the key is saved later on the
 * template or the notification.
 * Example: the office uploads "diwali.mp4" (8 MB) → key {@code notifications/9b1e....mp4}, VIDEO, 45 seconds.
 */
@Service
public class NotificationMediaService {

	public static final String FOLDER = "notifications";

	/** A provider media id older than this is uploaded again (the provider keeps a file about 30 days). */
	public static final Duration REUPLOAD_AFTER = Duration.ofDays(25);

	private final FileStorage storage;

	private final MediaPublisher publisher;

	private final Clock clock;

	public NotificationMediaService(FileStorage storage, MediaPublisher publisher, Clock clock) {
		this.storage = storage;
		this.publisher = publisher;
		this.clock = clock;
	}

	/**
	 * Makes sure the provider has the notification's file: uploads it once, and again when the id is older than
	 * 25 days; the id and the time are kept on the notification. A notification with no file: nothing happens.
	 * Example: "Diwali greetings" with a video → one upload, then 247 messages point at the same id.
	 *
	 * @throws MediaPublishException when the provider did not take it (nothing is sent; tried again next minute)
	 */
	public void ensurePublished(Notification n) {
		if (n.getMediaKey() == null) {
			return;
		}
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		boolean fresh = n.getProviderMediaId() != null && n.getProviderMediaAt() != null
				&& n.getProviderMediaAt().plus(REUPLOAD_AFTER).isAfter(now);
		if (fresh) {
			return;
		}
		MediaRules.Format format = MediaRules.Format.ofKey(n.getMediaKey())
			.orElseThrow(() -> new MediaPublishException("Unknown file type: " + n.getMediaKey(), null));
		try {
			n.setProviderMediaId(publisher.publish(n.getMediaKey(), format));
			n.setProviderMediaAt(now);
		}
		catch (SmsSendException ex) {
			throw new MediaPublishException("The provider did not take the file of notification " + n.getId(), ex);
		}
	}

	/**
	 * @throws ApiException 400 VALIDATION (no file), 409 MEDIA_TOO_BIG, 409 MEDIA_WRONG_TYPE
	 */
	public NotificationMediaResponse save(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw ApiException.validation("file", "is empty");
		}
		// Refused before the bytes are read into memory.
		if (file.getSize() > MediaRules.MAX_BYTES) {
			throw tooBig("A file can be 16 MB at most. Make it smaller on your phone.");
		}
		byte[] bytes = read(file);
		MediaRules.Format format = MediaRules.detect(bytes)
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "MEDIA_WRONG_TYPE",
					"Send a JPEG or PNG picture, an MP4 video, a PDF, or an MP3, M4A, OGG or AMR audio file."));
		MediaFileType type = format.type();
		if (bytes.length > MediaRules.maxBytes(type)) {
			throw tooBig(MediaRules.tooBigMessage(type));
		}

		String key = write(bytes, format);
		Optional<int[]> size = (type == MediaFileType.IMAGE) ? MediaRules.imageSize(bytes) : Optional.empty();
		Integer seconds = (format == MediaRules.Format.MP4 || format == MediaRules.Format.M4A)
				? MediaRules.mp4Seconds(bytes).orElse(null) : null;
		return new NotificationMediaResponse(key, type, bytes.length, size.map(s -> s[0]).orElse(null),
				size.map(s -> s[1]).orElse(null), seconds);
	}

	/**
	 * A key from {@link #save} that exists and holds a file of this type. Used when a template or a notification
	 * names its file. Example: ("notifications/9b1e.mp4", VIDEO) is fine; a student photo key is refused.
	 *
	 * @param field the request field for the 400, example {@code mediaKey}
	 * @return the format of the file
	 * @throws ApiException 400 VALIDATION
	 */
	public MediaRules.Format requireFile(String key, MediaFileType wanted, String field) {
		MediaRules.Format format = (key != null && key.startsWith(FOLDER + "/"))
				? MediaRules.Format.ofKey(key).filter(f -> wanted == null || f.type() == wanted).orElse(null) : null;
		if (format == null) {
			throw ApiException.validation(field, "must be a " + ((wanted == null) ? "" : wanted + " ")
					+ "file from POST /notification-media");
		}
		try (java.io.InputStream in = storage.open(key)) {
			return format;
		}
		catch (IOException ex) {
			throw ApiException.validation(field, "does not exist");
		}
	}

	private static ApiException tooBig(String message) {
		return new ApiException(HttpStatus.CONFLICT, "MEDIA_TOO_BIG", message);
	}

	private static byte[] read(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The upload could not be read", ex);
		}
	}

	private String write(byte[] bytes, MediaRules.Format format) {
		try {
			return storage.save(FOLDER, bytes, format.extension());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The notification file could not be saved", ex);
		}
	}

}

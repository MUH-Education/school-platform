package com.muhjain.school.service.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.response.NotificationMediaResponse;
import com.muhjain.school.model.enums.MediaFileType;
import com.muhjain.school.service.storage.FileStorage;
import com.muhjain.school.util.MediaRules;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
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

	private final FileStorage storage;

	public NotificationMediaService(FileStorage storage) {
		this.storage = storage;
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

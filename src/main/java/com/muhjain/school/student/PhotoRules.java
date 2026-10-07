package com.muhjain.school.student;

import java.util.Optional;

/**
 * What a photo upload must look like (rule 18). The real type is read from the first bytes, because a file name or a
 * browser's "content type" can be anything.
 * Example: a PDF renamed to "photo.jpg" starts with "%PDF", not with FF D8 FF, so it is refused.
 */
final class PhotoRules {

	static final int MAX_BYTES = 2 * 1024 * 1024;

	private PhotoRules() {
	}

	/** The type of the picture. */
	enum Kind {

		JPEG("jpg", "image/jpeg"), PNG("png", "image/png");

		private final String extension;

		private final String contentType;

		Kind(String extension, String contentType) {
			this.extension = extension;
			this.contentType = contentType;
		}

		String extension() {
			return extension;
		}

		String contentType() {
			return contentType;
		}

		static Optional<Kind> ofExtension(String key) {
			for (Kind kind : values()) {
				if (key.endsWith("." + kind.extension)) {
					return Optional.of(kind);
				}
			}
			return Optional.empty();
		}

	}

	/** JPEG starts with FF D8 FF. PNG starts with 89 50 4E 47 0D 0A 1A 0A. Anything else → empty. */
	static Optional<Kind> detect(byte[] bytes) {
		if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
			return Optional.of(Kind.JPEG);
		}
		int[] png = { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
		if (bytes.length >= png.length) {
			for (int i = 0; i < png.length; i++) {
				if ((bytes[i] & 0xFF) != png[i]) {
					return Optional.empty();
				}
			}
			return Optional.of(Kind.PNG);
		}
		return Optional.empty();
	}

}

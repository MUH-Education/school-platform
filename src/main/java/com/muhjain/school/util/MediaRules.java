package com.muhjain.school.util;

import com.muhjain.school.model.enums.MediaFileType;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

/**
 * What a notification file may be (docs/10-notifications.md section 6). Like {@link PhotoRules}, the real type is
 * read from the first bytes, because a file name or a browser's "content type" can be anything.
 * <ul>
 * <li>IMAGE: JPEG or PNG, at most 5 MB</li>
 * <li>VIDEO: MP4, at most 16 MB (the server does not make it smaller)</li>
 * <li>DOCUMENT: PDF, at most 5 MB</li>
 * <li>AUDIO: MP3, M4A, OGG or AMR, at most 16 MB</li>
 * </ul>
 * Example: a PDF renamed to "poster.jpg" starts with "%PDF", so it is a DOCUMENT, not an IMAGE.
 */
public final class MediaRules {

	private static final int MB = 1024 * 1024;

	/** The biggest limit of any type. A bigger upload is refused before its bytes are read. */
	public static final int MAX_BYTES = 16 * MB;

	private MediaRules() {
	}

	/** One file format we accept. */
	public enum Format {

		JPEG(MediaFileType.IMAGE, "jpg", "image/jpeg"),
		PNG(MediaFileType.IMAGE, "png", "image/png"),
		MP4(MediaFileType.VIDEO, "mp4", "video/mp4"),
		PDF(MediaFileType.DOCUMENT, "pdf", "application/pdf"),
		MP3(MediaFileType.AUDIO, "mp3", "audio/mpeg"),
		M4A(MediaFileType.AUDIO, "m4a", "audio/mp4"),
		OGG(MediaFileType.AUDIO, "ogg", "audio/ogg"),
		AMR(MediaFileType.AUDIO, "amr", "audio/amr");

		private final MediaFileType type;

		private final String extension;

		private final String contentType;

		Format(MediaFileType type, String extension, String contentType) {
			this.type = type;
			this.extension = extension;
			this.contentType = contentType;
		}

		public MediaFileType type() {
			return type;
		}

		public String extension() {
			return extension;
		}

		public String contentType() {
			return contentType;
		}

		/** The format of a saved key. Example: {@code notifications/9b1e.mp4} → MP4. */
		public static Optional<Format> ofKey(String key) {
			for (Format format : values()) {
				if (key.endsWith("." + format.extension)) {
					return Optional.of(format);
				}
			}
			return Optional.empty();
		}

	}

	/** The limit of one type, in bytes. */
	public static int maxBytes(MediaFileType type) {
		return switch (type) {
			case IMAGE, DOCUMENT -> 5 * MB;
			case VIDEO, AUDIO -> 16 * MB;
		};
	}

	/** The limit as the office reads it. Example: "A video can be 16 MB at most. Make it smaller on your phone." */
	public static String tooBigMessage(MediaFileType type) {
		String what = switch (type) {
			case IMAGE -> "A picture";
			case VIDEO -> "A video";
			case DOCUMENT -> "A PDF";
			case AUDIO -> "A voice or audio file";
		};
		return what + " can be " + (maxBytes(type) / MB) + " MB at most. Make it smaller on your phone.";
	}

	/** The format from the first bytes, or empty when we do not accept it. */
	public static Optional<Format> detect(byte[] bytes) {
		Optional<PhotoRules.Kind> photo = PhotoRules.detect(bytes);
		if (photo.isPresent()) {
			return Optional.of((photo.get() == PhotoRules.Kind.JPEG) ? Format.JPEG : Format.PNG);
		}
		if (startsWith(bytes, 0, "%PDF-")) {
			return Optional.of(Format.PDF);
		}
		// MP4 and M4A: a box "ftyp" at byte 4, then the brand. "M4A " / "M4B " is audio, the rest is video.
		if (startsWith(bytes, 4, "ftyp") && bytes.length >= 12) {
			String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
			return Optional.of((brand.equals("M4A ") || brand.equals("M4B ")) ? Format.M4A : Format.MP4);
		}
		if (startsWith(bytes, 0, "OggS")) {
			return Optional.of(Format.OGG);
		}
		if (startsWith(bytes, 0, "#!AMR")) {
			return Optional.of(Format.AMR);
		}
		// MP3: an "ID3" tag, or straight away a frame (11 bits set).
		if (startsWith(bytes, 0, "ID3")
				|| (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0)) {
			return Optional.of(Format.MP3);
		}
		return Optional.empty();
	}

	/** Width and height of a JPEG or PNG, read from the header only. Empty if the picture cannot be read. */
	public static Optional<int[]> imageSize(byte[] bytes) {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			if (in == null) {
				return Optional.empty();
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				return Optional.empty();
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, true, true);
				return Optional.of(new int[] { reader.getWidth(0), reader.getHeight(0) });
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException ex) {
			return Optional.empty();
		}
	}

	/**
	 * The length in whole seconds of an MP4 or M4A, from its "moov/mvhd" box (time scale and duration).
	 * Example: duration 45 000 at time scale 1 000 → 45. Empty for other formats or a broken file.
	 */
	public static Optional<Integer> mp4Seconds(byte[] bytes) {
		long moov = findBox(bytes, 0, bytes.length, "moov");
		if (moov < 0) {
			return Optional.empty();
		}
		long moovEnd = moov + boxSize(bytes, (int) moov);
		long mvhd = findBox(bytes, (int) moov + 8, (int) Math.min(moovEnd, bytes.length), "mvhd");
		if (mvhd < 0) {
			return Optional.empty();
		}
		int p = (int) mvhd + 8;
		if (p + 4 > bytes.length) {
			return Optional.empty();
		}
		int version = bytes[p] & 0xFF;
		// version 0: created(4) modified(4) timescale(4) duration(4); version 1: created(8) modified(8) timescale(4)
		// duration(8). The 4 bytes before are version and flags.
		int timescaleAt = p + 4 + ((version == 1) ? 16 : 8);
		int durationAt = timescaleAt + 4;
		int durationLength = (version == 1) ? 8 : 4;
		if (durationAt + durationLength > bytes.length) {
			return Optional.empty();
		}
		long timescale = readUnsigned(bytes, timescaleAt, 4);
		long duration = readUnsigned(bytes, durationAt, durationLength);
		if (timescale <= 0) {
			return Optional.empty();
		}
		return Optional.of((int) Math.round((double) duration / timescale));
	}

	// The start of the first box of this type between from and to, or -1.
	private static long findBox(byte[] bytes, int from, int to, String type) {
		int p = from;
		while (p + 8 <= to) {
			long size = boxSize(bytes, p);
			if (size < 8) {
				return -1;
			}
			if (startsWith(bytes, p + 4, type)) {
				return p;
			}
			p += (int) Math.min(size, Integer.MAX_VALUE - p);
		}
		return -1;
	}

	// size 1 = a 64-bit size follows the type; size 0 = up to the end of the file.
	private static long boxSize(byte[] bytes, int p) {
		long size = readUnsigned(bytes, p, 4);
		if (size == 1 && p + 16 <= bytes.length) {
			return readUnsigned(bytes, p + 8, 8);
		}
		if (size == 0) {
			return bytes.length - p;
		}
		return size;
	}

	private static long readUnsigned(byte[] bytes, int p, int length) {
		long value = 0;
		for (int i = 0; i < length; i++) {
			value = (value << 8) | (bytes[p + i] & 0xFF);
		}
		return value;
	}

	private static boolean startsWith(byte[] bytes, int at, String text) {
		byte[] wanted = text.getBytes(StandardCharsets.US_ASCII);
		return bytes.length >= at + wanted.length
				&& Arrays.equals(bytes, at, at + wanted.length, wanted, 0, wanted.length);
	}

}

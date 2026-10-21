package com.muhjain.school.notification;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Small real files for the media tests, made in memory. */
final class MediaFiles {

	private MediaFiles() {
	}

	/** A real PNG picture of this size. */
	static byte[] png(int width, int height) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}

	/** A tiny MP4 (or M4A with brand "M4A "): an "ftyp" box, then "moov" holding an "mvhd" with the length. */
	static byte[] mp4(String brand, long timescale, long duration) {
		ByteBuffer ftyp = ByteBuffer.allocate(16);
		ftyp.putInt(16).put(ascii("ftyp")).put(ascii(brand)).putInt(0);
		ByteBuffer mvhd = ByteBuffer.allocate(108);
		mvhd.putInt(108).put(ascii("mvhd")).putInt(0)   // size, type, version 0 + flags
			.putInt(0).putInt(0)                          // created, modified
			.putInt((int) timescale).putInt((int) duration);
		ByteBuffer moov = ByteBuffer.allocate(8 + 108);
		moov.putInt(8 + 108).put(ascii("moov")).put(mvhd.array());
		return concat(ftyp.array(), moov.array());
	}

	/** This header, then zeros up to {@code size} bytes. */
	static byte[] padded(byte[] header, int size) {
		return Arrays.copyOf(header, size);
	}

	static byte[] ascii(String text) {
		return text.getBytes(StandardCharsets.US_ASCII);
	}

	static byte[] concat(byte[] a, byte[] b) {
		byte[] all = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, all, a.length, b.length);
		return all;
	}

}

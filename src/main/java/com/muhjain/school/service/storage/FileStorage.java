package com.muhjain.school.service.storage;

import com.muhjain.school.service.storage.impl.LocalFolderFileStorage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where the school's own files are kept: student photos and notification media (posters, videos, PDFs). The
 * database stores only the key. Example keys: {@code students/3f2a...c1.jpg}, {@code notifications/9b1e...07.mp4}.
 * <ul>
 * <li>{@code local} and {@code test}: {@link LocalFolderFileStorage}, a folder on the machine.</li>
 * <li>{@code prod}: an S3-style bucket in India (Phase 9).</li>
 * </ul>
 * A file has no public URL. A photo is served only by {@code GET /api/v1/students/{id}/photo}, after a permission
 * check; a notification file goes to the WhatsApp provider at send time ({@code MediaPublisher}).
 */
public interface FileStorage {

	/**
	 * Saves the bytes under a new random name and returns the key. The name is made by the server, never taken
	 * from the upload.
	 *
	 * @param folder {@code students} or {@code notifications}
	 * @param extension {@code jpg}, {@code png}, {@code mp4}, {@code pdf}, ...
	 */
	String save(String folder, byte[] bytes, String extension) throws IOException;

	/** @return the file, or throws {@link java.io.FileNotFoundException} if the key is unknown */
	InputStream open(String key) throws IOException;

	/** Removes the file. A key that is already gone is not an error. */
	void delete(String key) throws IOException;

}

package com.muhjain.school.student;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where student photos are kept. The database stores only the key. Example key: {@code students/3f2a...c1.jpg}.
 * <ul>
 * <li>{@code dev}: {@link LocalFolderPhotoStorage}, a folder on the machine.</li>
 * <li>{@code prod}: an S3-style bucket in India (Phase 9).</li>
 * </ul>
 * A photo has no public URL. It is served only by {@code GET /api/v1/students/{id}/photo}, after a permission check.
 */
public interface PhotoStorage {

	/**
	 * Saves the bytes and returns the key.
	 *
	 * @param extension {@code jpg} or {@code png}
	 */
	String save(byte[] bytes, String extension) throws IOException;

	/** @return the file, or throws {@link java.io.FileNotFoundException} if the key is unknown */
	InputStream open(String key) throws IOException;

	/** Removes the file. A key that is already gone is not an error. */
	void delete(String key) throws IOException;

}

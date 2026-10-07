package com.muhjain.school.student;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Photos in a folder on this machine ({@code app.photos.folder}, default {@code ./data/photos}).
 * Good for {@code dev} and {@code test}. The file name is made by the server, never taken from the upload, so a
 * client cannot choose a path. {@link #open} also checks that the key stays inside the folder.
 * Example: key {@code students/3f2a....jpg} is the file {@code ./data/photos/students/3f2a....jpg}.
 */
@Component
@ConditionalOnProperty(name = "app.photos.storage", havingValue = "local", matchIfMissing = true)
public class LocalFolderPhotoStorage implements PhotoStorage {

	private final Path root;

	public LocalFolderPhotoStorage(PhotoProperties properties) {
		this.root = properties.folder().toAbsolutePath().normalize();
	}

	@Override
	public String save(byte[] bytes, String extension) throws IOException {
		String key = "students/" + UUID.randomUUID() + "." + extension;
		Path file = resolve(key);
		Files.createDirectories(file.getParent());
		Files.write(file, bytes);
		return key;
	}

	@Override
	public InputStream open(String key) throws IOException {
		Path file = resolve(key);
		if (!Files.isRegularFile(file)) {
			throw new FileNotFoundException("No photo for this key");
		}
		return Files.newInputStream(file);
	}

	@Override
	public void delete(String key) throws IOException {
		Files.deleteIfExists(resolve(key));
	}

	// The key must stay inside the photo folder.
	private Path resolve(String key) throws IOException {
		Path file = root.resolve(key).normalize();
		if (!file.startsWith(root)) {
			throw new IOException("Photo key is outside the photo folder");
		}
		return file;
	}

}

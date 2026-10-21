package com.muhjain.school.service.storage.impl;

import com.muhjain.school.config.StorageProperties;
import com.muhjain.school.service.storage.FileStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * Files in a folder on this machine ({@code app.storage.folder}, default {@code ./data/photos}).
 * Good for {@code local} and {@code test}. The file name is made by the server, never taken from the upload, so a
 * client cannot choose a path. {@link #open} also checks that the key stays inside the folder.
 * Example: key {@code students/3f2a....jpg} is the file {@code ./data/photos/students/3f2a....jpg}.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFolderFileStorage implements FileStorage {

	private static final Set<String> FOLDERS = Set.of("students", "notifications");

	private final Path root;

	public LocalFolderFileStorage(StorageProperties properties) {
		this.root = properties.folder().toAbsolutePath().normalize();
	}

	@Override
	public String save(String folder, byte[] bytes, String extension) throws IOException {
		if (!FOLDERS.contains(folder) || !extension.matches("[a-z0-9]{2,4}")) {
			throw new IllegalArgumentException("Unknown folder or extension");
		}
		String key = folder + "/" + UUID.randomUUID() + "." + extension;
		Path file = resolve(key);
		Files.createDirectories(file.getParent());
		Files.write(file, bytes);
		return key;
	}

	@Override
	public InputStream open(String key) throws IOException {
		Path file = resolve(key);
		if (!Files.isRegularFile(file)) {
			throw new FileNotFoundException("No file for this key");
		}
		return Files.newInputStream(file);
	}

	@Override
	public void delete(String key) throws IOException {
		Files.deleteIfExists(resolve(key));
	}

	// The key must stay inside the storage folder.
	private Path resolve(String key) throws IOException {
		Path file = root.resolve(key).normalize();
		if (!file.startsWith(root)) {
			throw new IOException("File key is outside the storage folder");
		}
		return file;
	}

}

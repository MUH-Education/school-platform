package com.muhjain.school.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * {@code app.storage.*}: where photos and notification files are kept. Example: type = local,
 * folder = ./data/photos (the same folder the photos always used, so nothing has to move).
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(String type, Path folder) {

	public StorageProperties {
		if (type == null || type.isBlank()) {
			type = "local";
		}
		if (folder == null) {
			folder = Path.of("./data/photos");
		}
	}

}

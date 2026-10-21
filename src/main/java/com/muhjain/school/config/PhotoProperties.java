package com.muhjain.school.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/** {@code app.photos.*}. Example: storage = local, folder = ./data/photos. */
@ConfigurationProperties("app.photos")
public record PhotoProperties(String storage, Path folder) {

	public PhotoProperties {
		if (storage == null || storage.isBlank()) {
			storage = "local";
		}
		if (folder == null) {
			folder = Path.of("./data/photos");
		}
	}

}

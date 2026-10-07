package com.muhjain.school.student;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

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

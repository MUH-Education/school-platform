package com.muhjain.school.docs;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the real application.yml, part by part. The API docs must be OFF by default and in prod, and ON only in dev
 * and test. A new profile (for example "staging") therefore has no docs unless someone switches them on.
 */
class ApplicationYamlDocsTest {

	private static final String API_DOCS = "springdoc.api-docs.enabled";

	private static final String SWAGGER_UI = "springdoc.swagger-ui.enabled";

	private final List<PropertySource<?>> parts = load();

	private static List<PropertySource<?>> load() {
		try {
			return new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private PropertySource<?> part(String profile) {
		return parts.stream()
			.filter(p -> (profile == null) ? !p.containsProperty("spring.config.activate.on-profile")
					: profile.equals(p.getProperty("spring.config.activate.on-profile")))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no part for profile " + profile));
	}

	@Test
	void docsAreOffByDefault() {
		assertThat(part(null).getProperty(API_DOCS)).isEqualTo(false);
		assertThat(part(null).getProperty(SWAGGER_UI)).isEqualTo(false);
	}

	@Test
	void docsAreOffInProd() {
		assertThat(part("prod").getProperty(API_DOCS)).isEqualTo(false);
		assertThat(part("prod").getProperty(SWAGGER_UI)).isEqualTo(false);
	}

	@Test
	void docsAreOnInDevAndTest() {
		for (String profile : List.of("dev", "test")) {
			assertThat(part(profile).getProperty(API_DOCS)).as(profile).isEqualTo(true);
			assertThat(part(profile).getProperty(SWAGGER_UI)).as(profile).isEqualTo(true);
		}
	}

}

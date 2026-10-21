package com.muhjain.school.docs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the real config files. The API docs must be OFF by default and in prod, and ON only in local
 * and test. A new profile (for example "staging") therefore has no docs unless someone switches them on.
 */
class ApplicationYamlDocsTest {

	private static final String API_DOCS = "springdoc.api-docs.enabled";

	private static final String SWAGGER_UI = "springdoc.swagger-ui.enabled";

	private final List<PropertySource<?>> yamlParts = loadYaml();

	private static List<PropertySource<?>> loadYaml() {
		try {
			return new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private PropertySource<?> yamlPart(String profile) {
		return yamlParts.stream()
			.filter(p -> (profile == null) ? !p.containsProperty("spring.config.activate.on-profile")
					: profile.equals(p.getProperty("spring.config.activate.on-profile")))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no part for profile " + profile));
	}

	private static Properties loadProperties(String profile) {
		try {
			Properties properties = new Properties();
			properties.load(new ClassPathResource("application-" + profile + ".properties").getInputStream());
			return properties;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Test
	void docsAreOffByDefault() {
		assertThat(yamlPart(null).getProperty(API_DOCS)).isEqualTo(false);
		assertThat(yamlPart(null).getProperty(SWAGGER_UI)).isEqualTo(false);
	}

	@Test
	void docsAreOffInProd() {
		Properties prod = loadProperties("prod");

		assertThat(prod.getProperty(API_DOCS)).isEqualTo("false");
		assertThat(prod.getProperty(SWAGGER_UI)).isEqualTo("false");
	}

	@Test
	void docsAreOnInTest() {
		assertThat(yamlPart("test").getProperty(API_DOCS)).isEqualTo(true);
		assertThat(yamlPart("test").getProperty(SWAGGER_UI)).isEqualTo(true);
	}

	@Test
	void docsAreOnInLocal() {
		Properties local = loadProperties("local");

		assertThat(local.getProperty(API_DOCS)).isEqualTo("true");
		assertThat(local.getProperty(SWAGGER_UI)).isEqualTo("true");
	}

}

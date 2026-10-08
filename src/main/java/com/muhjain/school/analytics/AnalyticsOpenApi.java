package com.muhjain.school.analytics;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gives the filter parameters of the seven Analytics URLs a description and an example in the API docs.
 * The text is written once, with {@code @Schema} on the parts of {@link StudentFilter}; the query parameter
 * {@code className} reads the part {@code classNames}. Example in Swagger: className = 1-5.
 */
@Configuration(proxyBeanMethods = false)
class AnalyticsOpenApi {

	private static final Map<String, String> PART_OF_PARAMETER = Map.of("className", "classNames");

	@Bean
	ParameterCustomizer analyticsFilterNotes() {
		return (parameter, methodParameter) -> {
			if (!AnalyticsController.class.equals(methodParameter.getDeclaringClass())) {
				return parameter;
			}
			String part = PART_OF_PARAMETER.getOrDefault(parameter.getName(), parameter.getName());
			Arrays.stream(StudentFilter.class.getRecordComponents())
				.filter(c -> c.getName().equals(part))
				.map(RecordComponent::getAccessor)
				.map(accessor -> accessor.getAnnotation(Schema.class))
				.filter(java.util.Objects::nonNull)
				.findFirst()
				.ifPresent(note -> {
					parameter.setDescription(note.description());
					parameter.setExample(note.example());
				});
			return parameter;
		};
	}

}

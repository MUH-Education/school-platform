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
 * {@code className} reads the part {@code classNames}. Example in Swagger: "... Example: 1-5".
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
					// The example goes into the text, not into the example field: Swagger would fill every field with
					// its example on "Try it out", and the examples together (bus=NO and routeId=4) are a mix the
					// server refuses. With empty fields the first call is the whole school.
					parameter.setDescription(note.description() + " Example: " + note.example());
				});
			return parameter;
		};
	}

}

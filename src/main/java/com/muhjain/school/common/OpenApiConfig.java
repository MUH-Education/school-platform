package com.muhjain.school.common;

import java.util.List;
import java.util.regex.Pattern;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

/**
 * The API documentation (task 8.10). It is built from the controllers, so it cannot drift from the code.
 * The docs and the Swagger page are served only when {@code springdoc.api-docs.enabled} and
 * {@code springdoc.swagger-ui.enabled} are true (dev and test). In prod they are false and nothing is served.
 * <p>
 * What this class adds to every operation:
 * <ul>
 * <li>the {@code bearerAuth} lock (the "Authorize" button takes the JWT), except on the open URLs, which carry
 * {@code @SecurityRequirements} with no value;</li>
 * <li>the sentence "Needs FEES_EDIT." in the description, read from {@code @PreAuthorize}, so it is always the
 * permission the server really checks;</li>
 * <li>the answers 401 and 403 with the shared {@code ApiError} body;</li>
 * <li>an operation id made of the controller and the method, for example {@code analytics_summary}.</li>
 * </ul>
 * Example of what the React developer reads: "POST /students/{studentId}/payments — Record a payment. Needs FEES_EDIT."
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	/** The name of the JWT scheme. Operations refer to it. */
	public static final String BEARER = "bearerAuth";

	private static final String ERROR_SCHEMA = "ApiError";

	private static final Pattern AUTHORITY = Pattern.compile("'([A-Z_]+)'");

	@Bean
	OpenAPI schoolOpenApi() {
		Components components = new Components();
		components.addSecuritySchemes(BEARER,
				new SecurityScheme().type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.description("The token from POST /api/v1/auth/otp/verify. Paste only the token, without the word Bearer."));
		ResolvedSchema error = ModelConverters.getInstance()
			.resolveAsResolvedSchema(new AnnotatedType(ApiErrorResponse.class));
		if (error.referencedSchemas != null) {
			error.referencedSchemas.forEach(components::addSchemas);
		}
		components.addResponses("Unauthorized",
				errorResponse("No token, a bad token, an old token, or the user is turned off. Code UNAUTHENTICATED."));
		components.addResponses("Forbidden",
				errorResponse("Logged in, but the role does not have the permission. Code FORBIDDEN."));
		return new OpenAPI().info(new Info().title("MUH Jain Global School API")
			.version("v1")
			.description("""
					Backend of the school admin web app and the bus attendant phone app.
					Log in with phone and OTP, then send the token as `Authorization: Bearer <token>`.
					Every error has the same body: `{"error": "CODE", "message": "...", "fields": null}`.
					Only three URLs need no token: `POST /api/v1/auth/otp/request`, `POST /api/v1/auth/otp/verify` and `GET /actuator/health`."""))
			.tags(List.of(tag("Auth", "Log in with phone and OTP, see who I am, log out."),
					tag("Users", "App users and the roles with their permissions."),
					tag("Vehicles", "Vehicles and their documents."),
					tag("Staff", "Drivers, attendants and helpers, and who is assigned to which vehicle."),
					tag("Routes", "Routes, stops and seats used."),
					tag("Students", "Students, parents' phones, bus, photo, CSV import."),
					tag("Admissions", "The New admission form in one call."),
					tag("Trips", "The attendant's phone app: own route, boarding taps, children list."),
					tag("Bus status", "The office view of every bus."),
					tag("Messages", "Parent SMS and message templates."),
					tag("Enquiries", "Admission enquiries with stages and follow-ups."),
					tag("Fees", "School years, class fees, fee plans, payments."),
					tag("Analytics", "Filters, numbers for graphs, the student list and its CSV file."),
					tag("Settings", "School settings.")))
			.components(components);
	}

	private static io.swagger.v3.oas.models.tags.Tag tag(String name, String description) {
		return new io.swagger.v3.oas.models.tags.Tag().name(name).description(description);
	}

	private static ApiResponse errorResponse(String description) {
		return new ApiResponse().description(description)
			.content(new Content().addMediaType("application/json",
					new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))));
	}

	/** Adds the lock, the permission sentence and the 401 and 403 answers to every operation of our controllers. */
	@Bean
	OperationCustomizer permissionNotes() {
		return (operation, handlerMethod) -> {
			if (!handlerMethod.getBeanType().getName().startsWith("com.muhjain.school")) {
				return operation;
			}
			// A stable, readable id for the React developer's code generator: analytics_summary, vehicle_update.
			// (The default, update_3, changes when a controller is added.)
			String controller = handlerMethod.getBeanType().getSimpleName().replaceFirst("Controller$", "");
			operation.setOperationId(Character.toLowerCase(controller.charAt(0)) + controller.substring(1) + "_"
					+ handlerMethod.getMethod().getName());
			boolean open = handlerMethod.hasMethodAnnotation(SecurityRequirements.class);
			String note;
			if (open) {
				operation.setSecurity(List.of());
				note = "Open: no token needed.";
			}
			else {
				operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
				operation.getResponses().addApiResponse("401", new ApiResponse().$ref("#/components/responses/Unauthorized"));
				operation.getResponses().addApiResponse("403", new ApiResponse().$ref("#/components/responses/Forbidden"));
				note = permissionSentence(handlerMethod);
			}
			String description = operation.getDescription();
			if (description == null || description.isBlank()) {
				operation.setDescription(note);
			}
			else if (!description.contains("Needs ") && !description.contains(note)) {
				operation.setDescription(description + "\n\n" + note);
			}
			return operation;
		};
	}

	// "Needs FEES_EDIT." / "Needs TRIPS_RECORD or TRIPS_RECORD_ANY." / "Needs a valid token (any role)."
	static String permissionSentence(HandlerMethod handler) {
		PreAuthorize rule = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
		if (rule == null) {
			rule = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
		}
		if (rule == null) {
			return "Needs a valid token (any role).";
		}
		List<String> permissions = AUTHORITY.matcher(rule.value())
			.results()
			.map(found -> found.group(1))
			.distinct()
			.toList();
		String joiner = rule.value().contains(" and ") ? " and " : " or ";
		return "Needs " + String.join(joiner, permissions) + ".";
	}

}

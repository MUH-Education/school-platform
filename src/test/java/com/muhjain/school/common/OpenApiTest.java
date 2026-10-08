package com.muhjain.school.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jayway.jsonpath.JsonPath;
import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.analytics.AnalyticsController;
import com.muhjain.school.fee.FeeController;
import com.muhjain.school.fee.PaymentController;
import com.muhjain.school.fee.SessionController;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Task 8.10. The test profile has the docs switched on, like dev. */
class OpenApiTest extends AbstractIntegrationTest {

	private static final Set<String> OPEN = Set.of("POST /api/v1/auth/otp/request", "POST /api/v1/auth/otp/verify");

	private static final Set<String> TAGS = Set.of("Auth", "Users", "Vehicles", "Staff", "Routes", "Students",
			"Admissions", "Trips", "Bus status", "Messages", "Enquiries", "Fees", "Analytics", "Settings");

	@Autowired
	@org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	Map<String, Object> docs;

	@BeforeEach
	void readDocs() throws Exception {
		String body = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		docs = JsonPath.read(body, "$");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Map<String, Map<String, Object>>> paths() {
		return (Map<String, Map<String, Map<String, Object>>>) docs.get("paths");
	}

	@Test
	void docsAreServedWithoutATokenWhenSwitchedOn() throws Exception {
		assertThat(docs.get("openapi").toString()).startsWith("3.");
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
		assertThat(JsonPath.<String>read(docs, "$.info.title")).isEqualTo("MUH Jain Global School API");
	}

	@Test
	void everyAnalyticsAndFeesUrlIsListed() {
		List<String> expected = new ArrayList<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
			Class<?> controller = entry.getValue().getBeanType();
			if (Set.of(AnalyticsController.class, FeeController.class, PaymentController.class, SessionController.class)
				.contains(controller)) {
				for (String pattern : entry.getKey().getPathPatternsCondition().getPatternValues()) {
					for (RequestMethod method : entry.getKey().getMethodsCondition().getMethods()) {
						expected.add(method.name().toLowerCase() + " " + pattern);
					}
				}
			}
		}
		// 7 analytics + 4 fees of one student + 1 payment list + 4 sessions = 16
		assertThat(expected).hasSize(16).contains("get /api/v1/analytics/summary", "get /api/v1/analytics/students.csv",
				"put /api/v1/students/{studentId}/fee-plan", "post /api/v1/students/{studentId}/payment-corrections");
		for (String operation : expected) {
			String[] parts = operation.split(" ");
			assertThat(paths()).as(operation).containsKey(parts[1]);
			assertThat(paths().get(parts[1])).as(operation).containsKey(parts[0]);
		}
	}

	@Test
	void everyOperationExceptTheOpenOnesDeclaresBearerAuth() {
		int operations = 0;
		for (var path : paths().entrySet()) {
			for (var op : path.getValue().entrySet()) {
				String key = op.getKey().toUpperCase() + " " + path.getKey();
				Object security = op.getValue().get("security");
				operations++;
				if (OPEN.contains(key)) {
					assertThat(security).as(key).isEqualTo(List.of());
				}
				else {
					assertThat(security).as(key).isEqualTo(List.of(Map.of("bearerAuth", List.of())));
				}
			}
		}
		assertThat(operations).isGreaterThan(70);
		assertThat(OPEN).allSatisfy(key -> assertThat(paths().get(key.split(" ")[1])).containsKey(key.split(" ")[0].toLowerCase()));
	}

	@Test
	void everyOperationHasATagASummaryAndTheNeededPermission() {
		for (var path : paths().entrySet()) {
			for (var op : path.getValue().entrySet()) {
				String key = op.getKey().toUpperCase() + " " + path.getKey();
				Map<String, Object> operation = op.getValue();
				assertThat((String) operation.get("summary")).as(key + " summary").isNotBlank();
				assertThat(TAGS).as(key + " tag").containsAll(castList(operation.get("tags")));
				assertThat(castList(operation.get("tags"))).as(key + " tag").hasSize(1);
				String description = (String) operation.get("description");
				assertThat(description).as(key + " description").matches("(?s).*(Needs |Open: ).*");
			}
		}
		assertThat(description("get", "/api/v1/analytics/summary")).isEqualTo("Needs ANALYTICS_VIEW.");
		assertThat(description("put", "/api/v1/students/{studentId}/fee-plan")).isEqualTo("Needs FEES_EDIT.");
		assertThat(description("post", "/api/v1/students/{studentId}/payment-corrections"))
			.isEqualTo("Needs FEES_CORRECT.");
		assertThat(description("post", "/api/v1/trips/marks")).isEqualTo("Needs TRIPS_RECORD or TRIPS_RECORD_ANY.");
		assertThat(description("get", "/api/v1/users")).isEqualTo("Needs USERS_MANAGE.");
		assertThat(description("get", "/api/v1/sessions")).isEqualTo("Needs a valid token (any role).");
		assertThat(description("post", "/api/v1/auth/otp/verify")).isEqualTo("Open: no token needed.");
		assertThat(description("post", "/api/v1/admissions")).startsWith("Needs ADMISSIONS_CREATE.")
			.contains("FEES_EDIT");
	}

	@Test
	void securedOperationsAnswer401And403WithTheSharedErrorBody() {
		Map<String, Object> responses = castMap(paths().get("/api/v1/analytics/summary").get("get").get("responses"));
		assertThat(castMap(responses.get("401")).get("$ref")).isEqualTo("#/components/responses/Unauthorized");
		assertThat(castMap(responses.get("403")).get("$ref")).isEqualTo("#/components/responses/Forbidden");
		Map<String, Object> components = castMap(docs.get("components"));
		Map<String, Object> schemas = castMap(components.get("schemas"));
		Map<String, Object> apiError = castMap(schemas.get("ApiError"));
		assertThat(castMap(apiError.get("properties"))).containsKeys("error", "message", "fields");
		assertThat(castMap(components.get("responses"))).containsKeys("Unauthorized", "Forbidden");
		assertThat(castMap(components.get("securitySchemes")).get("bearerAuth"))
			.isEqualTo(Map.of("type", "http", "description",
					"The token from POST /api/v1/auth/otp/verify. Paste only the token, without the word Bearer.",
					"scheme", "bearer", "bearerFormat", "JWT"));
	}

	@Test
	void mainRequestRecordsHaveExamples() {
		Map<String, Object> schemas = castMap(castMap(docs.get("components")).get("schemas"));
		for (String name : List.of("AdmissionRequest", "FeePlanRequest", "PaymentRequest", "CorrectionRequest")) {
			assertThat(castMap(schemas.get(name))).as(name).containsKey("example");
		}
		List<Map<String, Object>> parameters = castList(paths().get("/api/v1/analytics/students").get("get").get("parameters"));
		assertThat(parameters).filteredOn(p -> "className".equals(p.get("name"))).singleElement()
			.satisfies(p -> assertThat(p.get("example")).isEqualTo("1-5"));
		assertThat(parameters).filteredOn(p -> "feeStatus".equals(p.get("name"))).singleElement()
			.satisfies(p -> assertThat(p.get("example")).isEqualTo("DELAYED"));
	}

	@Test
	void openDocsDoNotOpenTheApi() throws Exception {
		// Swagger is only a page. Every API call still needs a real token and the right role.
		mockMvc.perform(get("/api/v1/analytics/summary")).andExpect(status().isUnauthorized());
		String transport = tokenFor(addUser("+919812340003", Role.TRANSPORT_INCHARGE));
		mockMvc.perform(get("/api/v1/analytics/summary").header("Authorization", bearer(transport)))
			.andExpect(status().isForbidden());
		String owner = tokenFor(addUser("+919812340001", Role.OWNER));
		mockMvc.perform(get("/api/v1/analytics/summary").header("Authorization", bearer(owner)))
			.andExpect(status().isOk());
	}

	@SuppressWarnings("unchecked")
	private static <T> List<T> castList(Object value) {
		return (List<T>) value;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> castMap(Object value) {
		return (Map<String, Object>) value;
	}

	private String description(String method, String path) {
		return (String) paths().get(path).get(method).get("description");
	}

}

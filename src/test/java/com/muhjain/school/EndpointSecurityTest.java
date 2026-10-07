package com.muhjain.school;

import java.util.stream.Stream;

import com.muhjain.school.user.Role;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security rule 5: every endpoint of Phase 1 has "no token → 401" and "wrong role → 403".
 * "Any login" endpoints have no wrong role, so the smallest role (ATTENDANT) must get through.
 */
class EndpointSecurityTest extends AbstractIntegrationTest {

	private static final String USER_BODY = "{\"phone\":\"9812340077\",\"role\":\"OFFICE_ADMIN\",\"active\":true}";

	private static final String SETTINGS_BODY = "{\"values\":{\"fees.grace_days\":\"10\"}}";

	// The bodies must be valid. Spring checks the body before the permission, so a bad body would give 400.
	private static final String VEHICLE_BODY = "{\"name\":\"Van 4\",\"registrationNo\":\"HR 23 A 1104\","
			+ "\"vehicleType\":\"SMALL_VAN\",\"seats\":14,\"monthlyCost\":30300,\"ownedBy\":\"CONTRACTOR\"}";

	private static final String VEHICLE_UPDATE_BODY = VEHICLE_BODY.replace("}", ",\"active\":true}");

	private static final String STAFF_BODY = "{\"name\":\"Balwan\",\"phone\":\"9812340011\",\"staffType\":\"ATTENDANT\"}";

	private static final String STAFF_UPDATE_BODY = STAFF_BODY.replace("}", ",\"active\":true}");

	private static final String ASSIGNMENT_BODY = "{\"duty\":\"DRIVER\",\"staffId\":1,\"fromDate\":\"2026-10-12\","
			+ "\"toDate\":\"2026-10-16\",\"temporary\":true}";

	private static final String ROUTE_BODY = "{\"name\":\"Route 4\"}";

	private static final String ROUTE_UPDATE_BODY = "{\"name\":\"Route 4\",\"active\":true}";

	private static final String STOPS_BODY = "[{\"name\":\"Jakhal\",\"morningTime\":\"07:40\"}]";

	private static final String DOCUMENTS_BODY = "{\"insurance\":\"2026-10-28\"}";

	/** method, URL, body, a role WITHOUT the permission (null = any login is enough) */
	static Stream<Arguments> protectedEndpoints() {
		return Stream.of(
				Arguments.of("GET", "/api/v1/auth/me", null, null),
				Arguments.of("POST", "/api/v1/auth/logout", null, null),
				Arguments.of("GET", "/api/v1/users", null, Role.OFFICE_ADMIN),
				Arguments.of("POST", "/api/v1/users", USER_BODY, Role.OFFICE_ADMIN),
				Arguments.of("PUT", "/api/v1/users/1", USER_BODY, Role.TRANSPORT_INCHARGE),
				Arguments.of("GET", "/api/v1/roles", null, Role.ADMISSIONS_DESK),
				Arguments.of("GET", "/api/v1/settings", null, null),
				Arguments.of("PUT", "/api/v1/settings", SETTINGS_BODY, Role.ATTENDANT),
				// Phase 2: reading needs VEHICLES_VIEW (not the admissions desk), changing needs VEHICLES_EDIT
				// (not the office admin).
				Arguments.of("GET", "/api/v1/vehicles", null, Role.ADMISSIONS_DESK),
				Arguments.of("POST", "/api/v1/vehicles", VEHICLE_BODY, Role.OFFICE_ADMIN),
				Arguments.of("GET", "/api/v1/vehicles/1", null, Role.ADMISSIONS_DESK),
				Arguments.of("PUT", "/api/v1/vehicles/1", VEHICLE_UPDATE_BODY, Role.OFFICE_ADMIN),
				Arguments.of("DELETE", "/api/v1/vehicles/1", null, Role.OFFICE_ADMIN),
				Arguments.of("PUT", "/api/v1/vehicles/1/documents", DOCUMENTS_BODY, Role.OFFICE_ADMIN),
				Arguments.of("GET", "/api/v1/vehicles/attention", null, Role.ADMISSIONS_DESK),
				Arguments.of("GET", "/api/v1/vehicles/1/assignments", null, Role.ADMISSIONS_DESK),
				Arguments.of("POST", "/api/v1/vehicles/1/assignments", ASSIGNMENT_BODY, Role.OFFICE_ADMIN),
				// Routes: reading needs ROUTES_VIEW (not the admissions desk), changing needs ROUTES_EDIT (not the
				// office admin).
				Arguments.of("GET", "/api/v1/routes", null, Role.ADMISSIONS_DESK),
				Arguments.of("POST", "/api/v1/routes", ROUTE_BODY, Role.OFFICE_ADMIN),
				Arguments.of("GET", "/api/v1/routes/load-board", null, Role.ADMISSIONS_DESK),
				Arguments.of("GET", "/api/v1/routes/1", null, Role.ADMISSIONS_DESK),
				Arguments.of("PUT", "/api/v1/routes/1", ROUTE_UPDATE_BODY, Role.OFFICE_ADMIN),
				Arguments.of("DELETE", "/api/v1/routes/1", null, Role.OFFICE_ADMIN),
				Arguments.of("PUT", "/api/v1/routes/1/stops", STOPS_BODY, Role.OFFICE_ADMIN),
				Arguments.of("GET", "/api/v1/staff", null, Role.ADMISSIONS_DESK),
				Arguments.of("POST", "/api/v1/staff", STAFF_BODY, Role.OFFICE_ADMIN),
				Arguments.of("PUT", "/api/v1/staff/1", STAFF_UPDATE_BODY, Role.OFFICE_ADMIN),
				Arguments.of("DELETE", "/api/v1/staff/1", null, Role.OFFICE_ADMIN));
	}

	@ParameterizedTest(name = "{0} {1} without token → 401")
	@MethodSource("protectedEndpoints")
	void noTokenGives401(String method, String url, String body, Role wrongRole) throws Exception {
		mockMvc.perform(call(method, url, body))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
	}

	@ParameterizedTest(name = "{0} {1} with wrong role {3} → 403")
	@MethodSource("protectedEndpoints")
	void wrongRoleGives403(String method, String url, String body, Role wrongRole) throws Exception {
		if (wrongRole == null) {
			String token = tokenFor(addUser("+919812340055", Role.ATTENDANT));
			mockMvc.perform(call(method, url, body).header("Authorization", bearer(token)))
				.andExpect(status().is(allOf(not(401), not(403))));
			return;
		}
		String token = tokenFor(addUser("+919812340055", wrongRole));
		mockMvc.perform(call(method, url, body).header("Authorization", bearer(token)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("FORBIDDEN"));
	}

	@ParameterizedTest(name = "{0} {1} is open")
	@MethodSource("openEndpoints")
	void openEndpointsNeedNoToken(String method, String url, String body) throws Exception {
		// Example: verify with a wrong code is 401 OTP_INVALID. That is the login answer, not "please log in".
		mockMvc.perform(call(method, url, body))
			.andExpect(status().is(not(403)))
			.andExpect(content().string(not(containsString("UNAUTHENTICATED"))));
	}

	static Stream<Arguments> openEndpoints() {
		return Stream.of(Arguments.of("POST", "/api/v1/auth/otp/request", "{\"phone\":\"9812340066\"}"),
				Arguments.of("POST", "/api/v1/auth/otp/verify", "{\"phone\":\"9812340066\",\"otp\":\"123456\"}"),
				Arguments.of("GET", "/actuator/health", null));
	}

	private static MockHttpServletRequestBuilder call(String method, String url, String body) {
		MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), url);
		if (body != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return builder;
	}

}

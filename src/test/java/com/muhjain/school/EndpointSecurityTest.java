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

	private static final String MARKS_BODY = "{\"marks\":[{\"studentId\":1,\"eventType\":\"BOARDED_MORNING\","
			+ "\"outcome\":\"DONE\",\"serviceDate\":\"2026-10-07\",\"occurredAt\":\"2026-10-07T07:42:10+05:30\"}]}";

	private static final String ENQUIRY_BODY = "{\"parentName\":\"Ramesh Jain\",\"phone\":\"9812340208\","
			+ "\"village\":\"Jakhal\",\"classSought\":\"3\",\"source\":\"WALK_IN\"}";

	private static final String SESSION_BODY = "{\"name\":\"2027-28\",\"startsOn\":\"2027-04-01\","
			+ "\"endsOn\":\"2028-03-31\",\"current\":false}";

	private static final String CLASS_FEES_BODY = "{\"fees\":[{\"className\":\"3\",\"schoolFee\":30000}]}";

	private static final String FEE_PLAN_BODY = "{\"schoolFee\":30000,\"busFee\":8800,"
			+ "\"payFrequency\":\"QUARTERLY\"}";

	private static final String PAYMENT_BODY = "{\"mode\":\"UPI\",\"lines\":[{\"feeHead\":\"SCHOOL\","
			+ "\"amount\":7500}]}";

	private static final String CORRECTION_BODY = "{\"receiptNo\":\"R-2026-0001\",\"feeHead\":\"SCHOOL\","
			+ "\"amount\":-500,\"note\":\"Typed wrong\"}";

	private static final String TEMPLATE_BODY = "{\"body\":\"{name} {time}\",\"active\":true}";

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
				Arguments.of("DELETE", "/api/v1/staff/1", null, Role.OFFICE_ADMIN),
				// Phase 4: trips need TRIPS_RECORD or TRIPS_RECORD_ANY (not the admissions desk). my-route needs
				// TRIPS_RECORD (not the office admin). Bus status needs BUS_STATUS_VIEW (not an attendant).
				Arguments.of("GET", "/api/v1/trips/my-route", null, Role.OFFICE_ADMIN),
				Arguments.of("GET", "/api/v1/trips/manifest", null, Role.ADMISSIONS_DESK),
				Arguments.of("POST", "/api/v1/trips/marks", MARKS_BODY, Role.ADMISSIONS_DESK),
				Arguments.of("GET", "/api/v1/bus-status", null, Role.ATTENDANT),
				Arguments.of("GET", "/api/v1/bus-status/routes/1", null, Role.ATTENDANT),
				Arguments.of("GET", "/api/v1/bus-status/attention", null, Role.ATTENDANT),
				// Phase 5: the Messages screen needs MESSAGES_VIEW (not an attendant); a text needs SETTINGS_EDIT.
				Arguments.of("GET", "/api/v1/messages", null, Role.ATTENDANT),
				Arguments.of("GET", "/api/v1/messages/summary", null, Role.ATTENDANT),
				Arguments.of("GET", "/api/v1/message-templates", null, Role.ATTENDANT),
				Arguments.of("PUT", "/api/v1/message-templates/REACHED_SCHOOL_M", TEMPLATE_BODY, Role.OFFICE_ADMIN),
				// Phase 6: enquiries need ENQUIRIES_VIEW / ENQUIRIES_EDIT (not the transport in-charge); the prefill
				// needs ADMISSIONS_CREATE (not the transport in-charge).
				Arguments.of("GET", "/api/v1/enquiries", null, Role.TRANSPORT_INCHARGE),
				Arguments.of("GET", "/api/v1/enquiries/summary", null, Role.TRANSPORT_INCHARGE),
				Arguments.of("POST", "/api/v1/enquiries", ENQUIRY_BODY, Role.TRANSPORT_INCHARGE),
				Arguments.of("GET", "/api/v1/enquiries/1", null, Role.ATTENDANT),
				Arguments.of("PUT", "/api/v1/enquiries/1", ENQUIRY_BODY, Role.TRANSPORT_INCHARGE),
				Arguments.of("POST", "/api/v1/enquiries/1/follow-ups", "{\"note\":\"Called\"}", Role.ATTENDANT),
				Arguments.of("POST", "/api/v1/enquiries/1/status", "{\"status\":\"CONTACTED\"}", Role.ATTENDANT),
				Arguments.of("GET", "/api/v1/enquiries/1/prefill", null, Role.TRANSPORT_INCHARGE),
				// Phase 7: sessions are readable by any login; class fees need FEES_VIEW (not the transport
				// in-charge); adding a session or saving class fees needs SETTINGS_EDIT (not the office admin).
				Arguments.of("GET", "/api/v1/sessions", null, null),
				Arguments.of("POST", "/api/v1/sessions", SESSION_BODY, Role.OFFICE_ADMIN),
				Arguments.of("GET", "/api/v1/sessions/1/class-fees", null, Role.TRANSPORT_INCHARGE),
				Arguments.of("PUT", "/api/v1/sessions/1/class-fees", CLASS_FEES_BODY, Role.OFFICE_ADMIN),
				// Phase 7: the plan needs FEES_EDIT (not the transport in-charge).
				Arguments.of("PUT", "/api/v1/students/1/fee-plan", FEE_PLAN_BODY, Role.TRANSPORT_INCHARGE),
				// Phase 7: a payment needs FEES_EDIT (not the transport in-charge); a correction needs FEES_CORRECT,
				// which only the owner has (not even the office admin).
				Arguments.of("POST", "/api/v1/students/1/payments", PAYMENT_BODY, Role.TRANSPORT_INCHARGE),
				Arguments.of("POST", "/api/v1/students/1/payment-corrections", CORRECTION_BODY, Role.OFFICE_ADMIN),
				// Phase 7: reading a child's fees needs FEES_VIEW (not the transport in-charge).
				Arguments.of("GET", "/api/v1/students/1/fees", null, Role.TRANSPORT_INCHARGE),
				// Phase 7: the payment list needs FEES_VIEW (not the transport in-charge).
				Arguments.of("GET", "/api/v1/payments?from=2026-10-01&to=2026-10-07", null, Role.TRANSPORT_INCHARGE));
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

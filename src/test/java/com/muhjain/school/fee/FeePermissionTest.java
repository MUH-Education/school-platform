package com.muhjain.school.fee;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "TRANSPORT_INCHARGE gets 403 on all fee URLs" (phase 7). The in-charge runs the buses, not the money.
 * The ATTENDANT and the correction rule (owner only) are in EndpointSecurityTest and PaymentApiTest.
 */
class FeePermissionTest extends FeeTestBase {

	static Stream<Arguments> feeUrls() {
		return Stream.of(
				Arguments.of("POST", "/api/v1/sessions", "{\"name\":\"2027-28\",\"startsOn\":\"2027-04-01\","
						+ "\"endsOn\":\"2028-03-31\"}"),
				Arguments.of("GET", "/api/v1/sessions/1/class-fees", null),
				Arguments.of("PUT", "/api/v1/sessions/1/class-fees", "{\"fees\":[]}"),
				Arguments.of("GET", "/api/v1/students/1/fees", null),
				Arguments.of("PUT", "/api/v1/students/1/fee-plan",
						"{\"schoolFee\":30000,\"payFrequency\":\"QUARTERLY\"}"),
				Arguments.of("POST", "/api/v1/students/1/payments",
						"{\"mode\":\"UPI\",\"lines\":[{\"feeHead\":\"SCHOOL\",\"amount\":100}]}"),
				Arguments.of("POST", "/api/v1/students/1/payment-corrections",
						"{\"receiptNo\":\"R-2026-0001\",\"feeHead\":\"SCHOOL\",\"amount\":-1,\"note\":\"x\"}"),
				Arguments.of("GET", "/api/v1/payments", null));
	}

	@ParameterizedTest(name = "{0} {1} → 403 for the transport in-charge")
	@MethodSource("feeUrls")
	void transportInchargeGets403(String method, String url, String body) throws Exception {
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), url)
			.header("Authorization", bearer(transport));
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		mockMvc.perform(request).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
	}

}

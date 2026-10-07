package com.muhjain.school.fee;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /payments}: date filter and paging. Today is 7 Oct 2026. */
class PaymentListApiTest extends FeeTestBase {

	long aryan;

	@BeforeEach
	void threePaymentsOnThreeDays() throws Exception {
		aryan = addStudent("A-2026-1", "2026-04-01");
		put(office, "/api/v1/students/" + aryan + "/fee-plan", FeePlanApiTest.QUARTERLY).andExpect(status().isOk());
		for (String day : new String[] { "2026-10-01", "2026-10-05", "2026-10-07" }) {
			post(office, "/api/v1/students/" + aryan + "/payments", "{\"mode\":\"CASH\",\"paidOn\":\"" + day
					+ "\",\"lines\":[{\"feeHead\":\"SCHOOL\",\"amount\":1000}]}").andExpect(status().isCreated());
		}
	}

	@Test
	void listsNewestFirstWithTheChildsName() throws Exception {
		get(desk, "/api/v1/payments").andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(3))
			.andExpect(jsonPath("$.items[0].paidOn").value("2026-10-07"))
			.andExpect(jsonPath("$.items[0].studentName").value("Aryan"))
			.andExpect(jsonPath("$.items[0].admissionNo").value("A-2026-1"))
			.andExpect(jsonPath("$.items[0].className").value("3"))
			.andExpect(jsonPath("$.items[2].paidOn").value("2026-10-01"));
	}

	@Test
	void dateFilterIsInclusiveAtBothEnds() throws Exception {
		get(office, "/api/v1/payments?from=2026-10-01&to=2026-10-05").andExpect(jsonPath("$.totalItems").value(2));
		get(office, "/api/v1/payments?from=2026-10-05").andExpect(jsonPath("$.totalItems").value(2));
		get(office, "/api/v1/payments?to=2026-09-30").andExpect(jsonPath("$.totalItems").value(0));
		get(office, "/api/v1/payments?from=2026-10-07&to=2026-10-07").andExpect(jsonPath("$.items", hasSize(1)));
	}

	@Test
	void pagingWorks() throws Exception {
		get(office, "/api/v1/payments?size=2&page=1").andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.page").value(1));
	}

	@Test
	void correctionsAreInTheListWithANegativeAmount() throws Exception {
		post(owner, "/api/v1/students/" + aryan + "/payment-corrections",
				"{\"receiptNo\":\"R-2026-0003\",\"feeHead\":\"SCHOOL\",\"amount\":-100,\"note\":\"Typed wrong\"}")
			.andExpect(status().isCreated());

		get(office, "/api/v1/payments?from=2026-10-07").andExpect(jsonPath("$.totalItems").value(2))
			.andExpect(jsonPath("$.items[0].amount").value(-100.0));
	}

	@Test
	void badParametersAreRefusedAndTransportInchargeIs403() throws Exception {
		get(office, "/api/v1/payments?from=2026-10-07&to=2026-10-01").andExpect(status().isBadRequest());
		get(office, "/api/v1/payments?size=0").andExpect(status().isBadRequest());
		get(office, "/api/v1/payments?size=101").andExpect(status().isBadRequest());
		get(office, "/api/v1/payments?page=-1").andExpect(status().isBadRequest());
		get(office, "/api/v1/payments?from=yesterday").andExpect(status().isBadRequest());
		get(transport, "/api/v1/payments").andExpect(status().isForbidden());
	}

}

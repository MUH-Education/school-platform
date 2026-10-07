package com.muhjain.school.fee;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Task 7.12a: the "Fee" column of the Students list. Today is 7 Oct 2026. */
class StudentListFeeStatusTest extends FeeTestBase {

	@BeforeEach
	void fourChildren() throws Exception {
		long onTime = addStudent("A-2026-1", "2026-04-01");
		long delayed = addStudent("A-2026-2", "2026-08-20");
		long defaulted = addStudent("A-2026-3", "2026-04-01");
		addStudent("A-2026-4", "2026-04-01"); // no plan
		put(office, "/api/v1/students/" + onTime + "/fee-plan", FeePlanApiTest.QUARTERLY).andExpect(status().isOk());
		post(office, "/api/v1/students/" + onTime + "/payments", "{\"mode\":\"CASH\",\"lines\":[{\"feeHead\":\"SCHOOL\","
				+ "\"amount\":22500},{\"feeHead\":\"BUS\",\"amount\":6600}]}").andExpect(status().isCreated());
		put(office, "/api/v1/students/" + delayed + "/fee-plan", "{\"schoolFee\":12000,\"payFrequency\":\"QUARTERLY\"}")
			.andExpect(status().isOk());
		put(office, "/api/v1/students/" + defaulted + "/fee-plan", FeePlanApiTest.QUARTERLY)
			.andExpect(status().isOk());
	}

	@Test
	void everyRowShowsItsFeeStatus() throws Exception {
		get(desk, "/api/v1/students?size=10").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.admissionNo=='A-2026-1')].feeStatus").value("ON_TIME"))
			.andExpect(jsonPath("$.items[?(@.admissionNo=='A-2026-2')].feeStatus").value("DELAYED"))
			.andExpect(jsonPath("$.items[?(@.admissionNo=='A-2026-3')].feeStatus").value("DEFAULTED"))
			.andExpect(jsonPath("$.items[?(@.admissionNo=='A-2026-4')].feeStatus").value(
					org.hamcrest.Matchers.contains((Object) null)));
	}

	@Test
	void aUserWithoutFeesViewSeesNoFeeStatus() throws Exception {
		get(transport, "/api/v1/students?size=10").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.admissionNo=='A-2026-3')].feeStatus").value(
					org.hamcrest.Matchers.contains((Object) null)));
	}

}

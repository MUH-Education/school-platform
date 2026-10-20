package com.muhjain.school.fee;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Task 7.12a: the "Fee" column of the Students list. Today is 7 Oct 2026. */
class StudentListFeeStatusTest extends FeeTestBase {

	@BeforeEach
	void fourChildren() throws Exception {
		long onTime = addStudent("A-2026-101", "2026-04-01");
		// Sep due is 36 days late on 7 Oct: past the grace day, inside the 60-day defaulted window.
		long delayed = addStudent("A-2026-102", "2026-09-01");
		// Apr due is 189 days late: past the 60-day window.
		long defaulted = addStudent("A-2026-103", "2026-04-01");
		addStudent("A-2026-104", "2026-04-01"); // no plan
		put(office, "/api/v1/students/" + onTime + "/fee-plan", FeePlanApiTest.STANDARD_PLAN)
			.andExpect(status().isOk());
		// April to October: 7 months at ₹2,500, fully paid, so nothing is pending.
		post(office, "/api/v1/students/" + onTime + "/payments",
				"{\"mode\":\"CASH\",\"lines\":[{\"feeHead\":\"SCHOOL\",\"amount\":17500}]}")
			.andExpect(status().isCreated());
		put(office, "/api/v1/students/" + delayed + "/fee-plan", FeePlanApiTest.STANDARD_PLAN)
			.andExpect(status().isOk());
		put(office, "/api/v1/students/" + defaulted + "/fee-plan", FeePlanApiTest.STANDARD_PLAN)
			.andExpect(status().isOk());
	}

	@Test
	void everyRowShowsItsFeeStatus() throws Exception {
		get(desk, "/api/v1/students?size=10").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.admissionNo=='A-2026-101')].feeStatus").value("ON_TIME"))
			.andExpect(jsonPath("$.items[?(@.admissionNo=='A-2026-102')].feeStatus").value("DELAYED"))
			.andExpect(jsonPath("$.items[?(@.admissionNo=='A-2026-103')].feeStatus").value("DEFAULTED"))
			.andExpect(jsonPath("$.items[?(@.admissionNo=='A-2026-104')].feeStatus").value(
					org.hamcrest.Matchers.contains((Object) null)));
	}

	@Test
	void aUserWithoutFeesViewSeesNoFeeStatus() throws Exception {
		get(transport, "/api/v1/students?size=10").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.admissionNo=='A-2026-103')].feeStatus").value(
					org.hamcrest.Matchers.contains((Object) null)));
	}

}

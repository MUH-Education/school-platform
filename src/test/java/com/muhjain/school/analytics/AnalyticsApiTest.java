package com.muhjain.school.analytics;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Analytics URLs, on the fixed 12-child school of {@link AnalyticsTestBase}. Today is 7 Oct 2026. */
class AnalyticsApiTest extends AnalyticsTestBase {

	private static final String URL = "/api/v1/analytics";

	@Test
	void summaryWithNoFilterCountsEveryActiveStudent() throws Exception {
		get(owner, URL + "/summary").andExpect(status().isOk())
			.andExpect(jsonPath("$.sessionName").value("2026-27"))
			.andExpect(jsonPath("$.students").value(12))
			.andExpect(jsonPath("$.studentsOnBus").value(5))
			.andExpect(jsonPath("$.busPercent").value(41.7))
			.andExpect(jsonPath("$.schoolFeeCollectedPercent").value(79.2))
			.andExpect(jsonPath("$.busFeeCollectedPercent").value(80.0))
			.andExpect(jsonPath("$.studentsWithPending").value(6));
	}

	@Test
	void summaryFollowsTheFilter() throws Exception {
		// Jakhal: Aarav (bus, paid), Bhavya (5000 of 7000), Charu (bus, bus unpaid), Kabir (paid).
		get(desk, URL + "/summary?village=jakhal").andExpect(status().isOk())
			.andExpect(jsonPath("$.students").value(4))
			.andExpect(jsonPath("$.studentsOnBus").value(2))
			.andExpect(jsonPath("$.busPercent").value(50.0))
			.andExpect(jsonPath("$.schoolFeeCollectedPercent").value(92.9))
			.andExpect(jsonPath("$.busFeeCollectedPercent").value(50.0))
			.andExpect(jsonPath("$.studentsWithPending").value(2));
		get(office, URL + "/summary?village=Jakhal&feeStatus=DELAYED").andExpect(status().isOk())
			.andExpect(jsonPath("$.students").value(1));
	}

	@Test
	void emptyFilterResultReturnsZerosNot404() throws Exception {
		get(owner, URL + "/summary?className=12").andExpect(status().isOk())
			.andExpect(jsonPath("$.students").value(0))
			.andExpect(jsonPath("$.studentsOnBus").value(0))
			.andExpect(jsonPath("$.busPercent").value(0.0))
			.andExpect(jsonPath("$.schoolFeeCollectedPercent").value(0.0))
			.andExpect(jsonPath("$.studentsWithPending").value(0));
	}

	@Test
	void aBadFilterIs400AndAnUnknownIdIs404() throws Exception {
		get(owner, URL + "/summary?className=7th").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$.fields.className").exists());
		get(owner, URL + "/summary?feeStatus=LATE").andExpect(status().isBadRequest());
		get(owner, URL + "/summary?sessionId=9999").andExpect(status().isNotFound());
		get(owner, URL + "/summary?routeId=9999").andExpect(status().isNotFound());
	}

	@Test
	void monthlyCollectionFollowsThePaymentsOldestDueFirst() throws Exception {
		// 11 children have a plan: ₹1,000 school a month each. Dev and Jiya paid 2 months, Bhavya, Gauri and Lata 5,
		// the other six 7. Bus: 5 children, ₹500 a month each; Charu paid nothing, so every month is 4 of 5.
		get(owner, URL + "/fee-collection-by-month").andExpect(status().isOk())
			.andExpect(jsonPath("$.months.length()").value(7))
			.andExpect(jsonPath("$.months[0].month").value("2026-04"))
			.andExpect(jsonPath("$.months[0].school.percent").value(100.0))
			.andExpect(jsonPath("$.months[2].school.percent").value(81.8))
			.andExpect(jsonPath("$.months[5].school.due").value(11000))
			.andExpect(jsonPath("$.months[5].school.collected").value(6000))
			.andExpect(jsonPath("$.months[5].school.percent").value(54.5))
			.andExpect(jsonPath("$.months[6].school.percent").value(54.5))
			.andExpect(jsonPath("$.months[0].bus.percent").value(80.0))
			.andExpect(jsonPath("$.months[6].bus.percent").value(80.0));
	}

	@Test
	void monthlyCollectionFollowsTheSameFilter() throws Exception {
		// Charu is the only Route A child with no bus money paid; Aarav and Lata paid all of theirs.
		get(owner, URL + "/fee-collection-by-month?routeId=" + routeA).andExpect(status().isOk())
			.andExpect(jsonPath("$.months[0].bus.percent").value(66.7))
			.andExpect(jsonPath("$.months[0].school.due").value(3000));
		get(owner, URL + "/fee-collection-by-month?className=12").andExpect(status().isOk())
			.andExpect(jsonPath("$.months.length()").value(7))
			.andExpect(jsonPath("$.months[0].school.due").value(0))
			.andExpect(jsonPath("$.months[0].school.percent").doesNotExist());
	}

}

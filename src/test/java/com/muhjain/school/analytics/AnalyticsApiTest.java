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

	@Test
	void occupationCountsAddUpToTheTotal() throws Exception {
		String all = get(owner, URL + "/payment-by-occupation").andExpect(status().isOk())
			.andExpect(jsonPath("$.occupations.length()").value(10))
			.andExpect(jsonPath("$.occupations[?(@.occupation=='FARMER_SMALL')].onTime").value(1))
			.andExpect(jsonPath("$.occupations[?(@.occupation=='FARMER_SMALL')].delayed").value(1))
			.andExpect(jsonPath("$.occupations[?(@.occupation=='SHOPKEEPER')].defaulted").value(1))
			.andExpect(jsonPath("$.occupations[?(@.occupation=='SHOPKEEPER')].total").value(2))
			.andExpect(jsonPath("$.occupations[?(@.occupation=='LABOUR')].noPlan").value(1))
			.andExpect(jsonPath("$.occupations[?(@.occupation=='FAMILY_ABROAD')].total").value(0))
			.andReturn()
			.getResponse()
			.getContentAsString();
		com.jayway.jsonpath.DocumentContext json = com.jayway.jsonpath.JsonPath.parse(all);
		int sum = 0;
		for (int i = 0; i < 10; i++) {
			int row = json.read("$.occupations[" + i + "].onTime", Integer.class)
					+ json.read("$.occupations[" + i + "].delayed", Integer.class)
					+ json.read("$.occupations[" + i + "].defaulted", Integer.class)
					+ json.read("$.occupations[" + i + "].noPlan", Integer.class);
			org.assertj.core.api.Assertions.assertThat(row).isEqualTo(json.read("$.occupations[" + i + "].total", Integer.class));
			sum += row;
		}
		org.assertj.core.api.Assertions.assertThat(sum).isEqualTo(12);
	}

	@Test
	void occupationFollowsTheFilter() throws Exception {
		get(owner, URL + "/payment-by-occupation?village=Jakhal&feeStatus=DELAYED").andExpect(status().isOk())
			.andExpect(jsonPath("$.occupations[?(@.occupation=='FARMER_SMALL')].delayed").value(1))
			.andExpect(jsonPath("$.occupations[?(@.occupation=='FARMER_SMALL')].onTime").value(0))
			.andExpect(jsonPath("$.occupations[?(@.occupation=='GOVT_EMPLOYEE')].total").value(0));
		get(owner, URL + "/payment-by-occupation?className=12").andExpect(status().isOk())
			.andExpect(jsonPath("$.occupations.length()").value(10))
			.andExpect(jsonPath("$.occupations[0].total").value(0));
	}

	@Test
	void classesWithNoStudentsAreReturnedAsZero() throws Exception {
		get(owner, URL + "/students-by-class").andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(12))
			.andExpect(jsonPath("$.classes.length()").value(15))
			.andExpect(jsonPath("$.classes[0].className").value("Nursery"))
			.andExpect(jsonPath("$.classes[0].students").value(0))
			.andExpect(jsonPath("$.classes[1].className").value("LKG"))
			.andExpect(jsonPath("$.classes[1].students").value(2))
			.andExpect(jsonPath("$.classes[?(@.className=='1')].students").value(2))
			.andExpect(jsonPath("$.classes[?(@.className=='3')].students").value(2))
			.andExpect(jsonPath("$.classes[?(@.className=='10')].students").value(1))
			.andExpect(jsonPath("$.classes[14].className").value("12"))
			.andExpect(jsonPath("$.classes[14].students").value(0));
		// A group filter keeps all 15 rows; the classes outside the group are 0.
		get(owner, URL + "/students-by-class?className=1-3").andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(6))
			.andExpect(jsonPath("$.classes.length()").value(15))
			.andExpect(jsonPath("$.classes[1].students").value(0));
	}

	@Test
	void villagesAreBiggestFirst() throws Exception {
		get(owner, URL + "/students-by-village").andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(12))
			.andExpect(jsonPath("$.villages.length()").value(4))
			.andExpect(jsonPath("$.villages[0].village").value("Jakhal"))
			.andExpect(jsonPath("$.villages[0].students").value(4))
			.andExpect(jsonPath("$.villages[1].village").value("Kalwa"))
			.andExpect(jsonPath("$.villages[2].village").value("Tohana"))
			.andExpect(jsonPath("$.villages[3].village").value("Dhand"))
			.andExpect(jsonPath("$.others.villages").value(0))
			.andExpect(jsonPath("$.others.students").value(0));
	}

	@Test
	void villagesBeyondTopEightAreGroupedAsOthers() throws Exception {
		for (String v : new String[] { "Alpur", "Badli", "Chamar", "Dabra", "Eral", "Fatehpur" }) {
			plainChild("Kid " + v, "4", v);
		}
		// 10 villages: Jakhal 4, Kalwa 3, Tohana 3, Dhand 2, then six with 1 child (by name). Top 8 ends at Dabra.
		get(owner, URL + "/students-by-village").andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(18))
			.andExpect(jsonPath("$.villages.length()").value(8))
			.andExpect(jsonPath("$.villages[7].village").value("Dabra"))
			.andExpect(jsonPath("$.others.villages").value(2))
			.andExpect(jsonPath("$.others.students").value(2));
	}

	@Test
	void villageSpellingWithOtherCapitalsIsOneVillage() throws Exception {
		plainChild("Kid One", "4", "jakhal");
		get(owner, URL + "/students-by-village").andExpect(status().isOk())
			.andExpect(jsonPath("$.villages[0].village").value("Jakhal"))
			.andExpect(jsonPath("$.villages[0].students").value(5));
	}

	@Test
	void villageFilterChangesEveryEndpointTheSameWay() throws Exception {
		String f = "?village=Jakhal";
		get(owner, URL + "/summary" + f).andExpect(jsonPath("$.students").value(4));
		get(owner, URL + "/students-by-class" + f).andExpect(jsonPath("$.total").value(4));
		get(owner, URL + "/students-by-village" + f).andExpect(jsonPath("$.total").value(4))
			.andExpect(jsonPath("$.villages.length()").value(1));
		get(owner, URL + "/payment-by-occupation" + f).andExpect(jsonPath("$.occupations[?(@.total>0)].total").value(
				org.hamcrest.Matchers.contains(2, 1, 1)));
		get(owner, URL + "/fee-collection-by-month" + f).andExpect(jsonPath("$.months[0].school.due").value(4000));
	}

	@Test
	void listHasOneRowPerStudentWithTheColumnsOfRule9() throws Exception {
		get(owner, URL + "/students?size=100").andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(12))
			.andExpect(jsonPath("$.items[0].name").value("Aarav"))
			.andExpect(jsonPath("$.items[?(@.name=='Charu')].className").value("2"))
			.andExpect(jsonPath("$.items[?(@.name=='Charu')].village").value("Jakhal"))
			.andExpect(jsonPath("$.items[?(@.name=='Charu')].fatherOccupation").value("GOVT_EMPLOYEE"))
			.andExpect(jsonPath("$.items[?(@.name=='Charu')].busRoute").value("Route A"))
			.andExpect(jsonPath("$.items[?(@.name=='Charu')].schoolFeeStatus").value("ON_TIME"))
			.andExpect(jsonPath("$.items[?(@.name=='Charu')].busFeeStatus").value("DEFAULTED"))
			.andExpect(jsonPath("$.items[?(@.name=='Charu')].feeStatus").value("DEFAULTED"))
			.andExpect(jsonPath("$.items[?(@.name=='Charu')].pendingAmount").value(3500.0))
			.andExpect(jsonPath("$.items[?(@.name=='Bhavya')].busFeeStatus").value(
					org.hamcrest.Matchers.contains((Object) null)))
			.andExpect(jsonPath("$.items[?(@.name=='Farhan')].schoolFeeStatus").value(
					org.hamcrest.Matchers.contains((Object) null)))
			.andExpect(jsonPath("$.items[?(@.name=='Farhan')].pendingAmount").value(0));
	}

	@Test
	void listIsPagedAndSortable() throws Exception {
		get(owner, URL + "/students?size=5&page=2").andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.totalPages").value(3))
			.andExpect(jsonPath("$.totalItems").value(12));
		// Pending: Dev 5000 and Jiya 5000 (tie by id), Charu 3500, then 2000s.
		get(owner, URL + "/students?size=3&sort=pendingAmount,desc").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].name").value("Dev"))
			.andExpect(jsonPath("$.items[1].name").value("Jiya"))
			.andExpect(jsonPath("$.items[2].name").value("Charu"));
		// School order: LKG, then 1, 2, 3 … 10 (not "10" before "2").
		get(owner, URL + "/students?size=100&sort=className,asc").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].className").value("LKG"))
			.andExpect(jsonPath("$.items[2].className").value("1"))
			.andExpect(jsonPath("$.items[11].className").value("10"));
		get(owner, URL + "/students?size=100&sort=name,desc").andExpect(jsonPath("$.items[0].name").value("Lata"));
		get(owner, URL + "/students?page=5").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void listRejectsBadPagingAndSort() throws Exception {
		get(owner, URL + "/students?sort=phone,asc").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.sort").exists());
		get(owner, URL + "/students?size=0").andExpect(status().isBadRequest());
		get(owner, URL + "/students?size=101").andExpect(status().isBadRequest());
		get(owner, URL + "/students?page=-1").andExpect(status().isBadRequest());
	}

	@Test
	void feeStatusFilterUsesTheSameRuleAsTheStudentPage() throws Exception {
		String body = get(owner, URL + "/students?size=100&feeStatus=DELAYED").andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(3))
			.andReturn().getResponse().getContentAsString();
		java.util.List<Integer> listed = com.jayway.jsonpath.JsonPath.read(body, "$.items[*].id");
		for (String name : ids.keySet()) {
			String page = get(owner, "/api/v1/students/" + ids.get(name) + "/fees").andReturn().getResponse().getContentAsString();
			Object own = com.jayway.jsonpath.JsonPath.read(page, "$.status");
			org.assertj.core.api.Assertions.assertThat(listed.contains(ids.get(name).intValue()))
				.as(name).isEqualTo("DELAYED".equals(own));
		}
	}

	@Test
	void summaryClassBarsAndListRowCountAreEqualForTheSameFilter() throws Exception {
		for (String f : new String[] { "", "?village=Jakhal", "?className=1-5&feeStatus=DEFAULTED", "?bus=YES",
				"?routeId=" + routeA, "?occupation=SHOPKEEPER" }) {
			String summary = get(owner, URL + "/summary" + f).andReturn().getResponse().getContentAsString();
			String classes = get(owner, URL + "/students-by-class" + f).andReturn().getResponse().getContentAsString();
			String list = get(owner, URL + "/students" + f + (f.isEmpty() ? "?" : "&") + "size=100").andReturn()
				.getResponse().getContentAsString();
			int fromSummary = com.jayway.jsonpath.JsonPath.read(summary, "$.students");
			java.util.List<Integer> bars = com.jayway.jsonpath.JsonPath.read(classes, "$.classes[*].students");
			int fromList = com.jayway.jsonpath.JsonPath.read(list, "$.totalItems");
			int rows = ((java.util.List<?>) com.jayway.jsonpath.JsonPath.read(list, "$.items")).size();
			org.assertj.core.api.Assertions.assertThat(bars.stream().mapToInt(Integer::intValue).sum())
				.as(f).isEqualTo(fromSummary);
			org.assertj.core.api.Assertions.assertThat(fromList).as(f).isEqualTo(fromSummary);
			org.assertj.core.api.Assertions.assertThat(rows).as(f).isEqualTo(fromSummary);
		}
	}

}

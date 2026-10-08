package com.muhjain.school.analytics;

import java.util.List;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.fee.FeeStatus;
import com.muhjain.school.student.BusFilter;
import com.muhjain.school.student.FatherOccupation;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsBaseTest extends AnalyticsTestBase {

	@Autowired
	AnalyticsBase base;

	private static StudentFilter filter(String className, String village, Long routeId, BusFilter bus,
			FatherOccupation occupation, FeeStatus feeStatus) {
		return StudentFilter.of(null, className, village, routeId, bus, occupation, feeStatus);
	}

	private List<String> names(StudentFilter filter) {
		return base.students(filter).stream().map(AnalyticsStudent::name).toList();
	}

	@Test
	void noFilterGivesEveryActiveChildByName() {
		assertThat(names(StudentFilter.none())).containsExactly("Aarav", "Bhavya", "Charu", "Dev", "Esha", "Farhan",
				"Gauri", "Harsh", "Ishan", "Jiya", "Kabir", "Lata");
	}

	@Test
	void aChildWhoLeftIsNotCounted() {
		jdbc.update("update student set status = 'LEFT', left_on = date '2026-09-01' where name = 'Dev'");
		assertThat(names(StudentFilter.none())).hasSize(11).doesNotContain("Dev");
	}

	@Test
	void villageClassRouteBusAndOccupationFilters() {
		assertThat(names(filter(null, "jakhal", null, null, null, null))).containsExactly("Aarav", "Bhavya", "Charu",
				"Kabir");
		assertThat(names(filter("1-3", null, null, null, null, null))).containsExactly("Aarav", "Bhavya", "Charu",
				"Dev", "Esha", "Farhan");
		assertThat(names(filter(null, null, routeA, null, null, null))).containsExactly("Aarav", "Charu", "Lata");
		assertThat(names(filter(null, null, null, BusFilter.YES, null, null))).hasSize(5);
		assertThat(names(filter(null, null, null, BusFilter.NO, null, null))).hasSize(7);
		assertThat(names(filter(null, null, null, null, FatherOccupation.SHOPKEEPER, null))).containsExactly("Dev",
				"Esha");
	}

	@Test
	void feeStatusFilterKeepsOnlyChildrenWithThatStatus() {
		assertThat(names(filter(null, null, null, null, null, FeeStatus.ON_TIME))).containsExactly("Aarav", "Esha",
				"Harsh", "Ishan", "Kabir");
		assertThat(names(filter(null, null, null, null, null, FeeStatus.DELAYED))).containsExactly("Bhavya", "Gauri",
				"Lata");
		assertThat(names(filter(null, null, null, null, null, FeeStatus.DEFAULTED))).containsExactly("Charu", "Dev",
				"Jiya");
		assertThat(names(filter(null, "Jakhal", null, null, null, FeeStatus.DELAYED))).containsExactly("Bhavya");
	}

	@Test
	void eachChildCarriesHisFeeNumbersAndRoute() {
		AnalyticsStudent charu = base.students(StudentFilter.none()).stream().filter(s -> s.name().equals("Charu")).findFirst().get();
		assertThat(charu.routeName()).isEqualTo("Route A");
		assertThat(charu.feeStatus()).isEqualTo(FeeStatus.DEFAULTED);
		assertThat(charu.headStatus(com.muhjain.school.fee.FeeHead.SCHOOL)).isEqualTo(FeeStatus.ON_TIME);
		assertThat(charu.pendingNow()).isEqualByComparingTo("3500");
		AnalyticsStudent farhan = base.students(StudentFilter.none()).stream().filter(s -> s.name().equals("Farhan")).findFirst().get();
		assertThat(farhan.fee()).isNull();
		assertThat(farhan.pendingNow()).isEqualByComparingTo("0");
	}

	@Test
	void theStatusIsTheSameAsOnTheChildsOwnPage() throws Exception {
		for (AnalyticsStudent s : base.students(StudentFilter.none())) {
			String body = get(owner, "/api/v1/students/" + s.id() + "/fees").andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
			assertThat((String) JsonPath.read(body, "$.status")).isEqualTo((s.feeStatus() == null) ? null : s.feeStatus().name());
			assertThat(new java.math.BigDecimal(JsonPath.read(body, "$.pendingNow").toString())).isEqualByComparingTo(s.pendingNow());
		}
	}

	@Test
	void anUnknownSessionOrRouteIsA404() {
		assertThatThrownBy(() -> base.students(StudentFilter.of(999L, null, null, null, null, null, null)))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
		assertThatThrownBy(() -> base.students(filter(null, null, 999L, null, null, null)))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
	}

	@Test
	void aFilterThatMatchesNobodyIsAnEmptyList() {
		assertThat(base.students(filter("12", null, null, null, null, null))).isEmpty();
	}

}

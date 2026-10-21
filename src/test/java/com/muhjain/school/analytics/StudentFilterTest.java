package com.muhjain.school.analytics;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.response.AnalyticsStudentFilter;
import com.muhjain.school.model.enums.BusFilter;
import com.muhjain.school.util.ClassNames;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentFilterTest {

	@Test
	void aSingleClassBecomesAListOfOne() {
		assertThat(AnalyticsStudentFilter.of(null, " lkg ", null, null, null, null, null).classNames()).containsExactly("LKG");
	}

	@Test
	void aGroupBecomesEveryClassInOrder() {
		assertThat(AnalyticsStudentFilter.of(null, "1-5", null, null, null, null, null).classNames())
			.containsExactly("1", "2", "3", "4", "5");
		assertThat(ClassNames.expand("LKG-2")).contains(List.of("LKG", "UKG", "1", "2"));
	}

	@Test
	void noClassMeansEveryClass() {
		assertThat(AnalyticsStudentFilter.of(null, null, null, null, null, null, null).classNames()).isNull();
		assertThat(AnalyticsStudentFilter.of(null, "  ", null, null, null, null, null).classNames()).isNull();
	}

	@Test
	void aWrongClassOrGroupIsA400OnClassName() {
		for (String bad : List.of("7th", "5-1", "1-", "1-5-8", "13", "a-b")) {
			assertThatThrownBy(() -> AnalyticsStudentFilter.of(null, bad, null, null, null, null, null))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getFields()).containsKey("className"));
		}
	}

	@Test
	void aRouteTogetherWithNoBusIsA400() {
		assertThatThrownBy(() -> AnalyticsStudentFilter.of(null, null, null, 4L, BusFilter.NO, null, null))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getFields()).containsKey("bus"));
	}

	@Test
	void villageIsTrimmedAndBlankMeansNone() {
		assertThat(AnalyticsStudentFilter.of(null, null, " Jakhal ", null, null, null, null).village()).isEqualTo("Jakhal");
		assertThat(AnalyticsStudentFilter.of(null, null, " ", null, null, null, null).village()).isNull();
	}

}

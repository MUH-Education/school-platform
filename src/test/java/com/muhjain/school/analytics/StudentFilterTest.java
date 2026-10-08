package com.muhjain.school.analytics;

import java.util.List;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.student.BusFilter;
import com.muhjain.school.student.ClassNames;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentFilterTest {

	@Test
	void aSingleClassBecomesAListOfOne() {
		assertThat(StudentFilter.of(null, " lkg ", null, null, null, null, null).classNames()).containsExactly("LKG");
	}

	@Test
	void aGroupBecomesEveryClassInOrder() {
		assertThat(StudentFilter.of(null, "1-5", null, null, null, null, null).classNames())
			.containsExactly("1", "2", "3", "4", "5");
		assertThat(ClassNames.expand("LKG-2")).contains(List.of("LKG", "UKG", "1", "2"));
	}

	@Test
	void noClassMeansEveryClass() {
		assertThat(StudentFilter.of(null, null, null, null, null, null, null).classNames()).isNull();
		assertThat(StudentFilter.of(null, "  ", null, null, null, null, null).classNames()).isNull();
	}

	@Test
	void aWrongClassOrGroupIsA400OnClassName() {
		for (String bad : List.of("7th", "5-1", "1-", "1-5-8", "13", "a-b")) {
			assertThatThrownBy(() -> StudentFilter.of(null, bad, null, null, null, null, null))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getFields()).containsKey("className"));
		}
	}

	@Test
	void aRouteTogetherWithNoBusIsA400() {
		assertThatThrownBy(() -> StudentFilter.of(null, null, null, 4L, BusFilter.NO, null, null))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getFields()).containsKey("bus"));
	}

	@Test
	void villageIsTrimmedAndBlankMeansNone() {
		assertThat(StudentFilter.of(null, null, " Jakhal ", null, null, null, null).village()).isEqualTo("Jakhal");
		assertThat(StudentFilter.of(null, null, " ", null, null, null, null).village()).isNull();
	}

}

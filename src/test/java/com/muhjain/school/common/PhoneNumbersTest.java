package com.muhjain.school.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersTest {

	@ParameterizedTest
	@ValueSource(strings = { "98123 45678", "09812345678", "+91-98123-45678", "9812345678", "+919812345678" })
	void validMobileBecomesPlus91Form(String input) {
		assertThat(PhoneNumbers.normalize(input)).isEqualTo("+919812345678");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "12345", "5812345678", "+1 9812345678", "98123abcde" })
	void invalidNumberIsRejected(String input) {
		assertThatThrownBy(() -> PhoneNumbers.normalize(input)).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(ex.getCode()).isEqualTo("VALIDATION");
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "+919812344321" })
	void maskShowsOnlyTheLastFourDigits(String phone) {
		assertThat(PhoneNumbers.mask(phone)).isEqualTo("+91XXXXXX4321");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "9812344321", "+91981234432" })
	void maskOfSomethingElseHidesEverything(String phone) {
		assertThat(PhoneNumbers.mask(phone)).isEqualTo("+91XXXXXXXXXX");
	}

}

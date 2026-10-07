package com.muhjain.school.fee;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTextTest {

	@Test
	void usesIndianGroupsAndHidesZeroPaise() {
		assertThat(MoneyText.of(new BigDecimal("9700.00"))).isEqualTo("₹9,700");
		assertThat(MoneyText.of(new BigDecimal("125000"))).isEqualTo("₹1,25,000");
		assertThat(MoneyText.of(new BigDecimal("7500.5"))).isEqualTo("₹7,500.50");
		assertThat(MoneyText.of(new BigDecimal("12345678"))).isEqualTo("₹1,23,45,678");
		assertThat(MoneyText.of(BigDecimal.ZERO)).isEqualTo("₹0");
		assertThat(MoneyText.of(new BigDecimal("-500"))).isEqualTo("-₹500");
	}

}

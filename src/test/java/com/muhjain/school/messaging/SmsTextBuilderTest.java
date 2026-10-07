package com.muhjain.school.messaging;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsTextBuilderTest {

	private static final String BOY = "{name} स्कूल पहुँच गया — {time}। MUH Jain School";

	private static final String EVENING_GIRL = "{name} छुट्टी की बस में चढ़ गई — {time}। MUH Jain School";

	private static final String LONGEST = "{name} अपने स्टॉप पर उतर गया — {time}। MUH Jain School";

	@Test
	void usesFirstWordOfNameAndTimeWithoutLeadingZeroInTheHour() {
		assertThat(SmsTextBuilder.build(BOY, "Aryan Jain", LocalTime.of(7, 42)))
			.isEqualTo("Aryan स्कूल पहुँच गया — 7:42। MUH Jain School");
	}

	@Test
	void minutesKeepTheirZero() {
		assertThat(SmsTextBuilder.build(BOY, "Aryan", LocalTime.of(14, 5))).contains("14:05");
	}

	@Test
	void girlTextIsUsedAsGiven() {
		assertThat(SmsTextBuilder.build(EVENING_GIRL, "Siya Goyal", LocalTime.of(14, 40))).contains("चढ़ गई");
	}

	@Test
	void longNameStillFitsIn70Characters() {
		for (String template : new String[] { BOY, EVENING_GIRL, LONGEST }) {
			String text = SmsTextBuilder.build(template, "Venkataramanujan Subramaniam", LocalTime.of(14, 40));
			assertThat(text.length()).isLessThanOrEqualTo(70);
			assertThat(text).contains("MUH Jain School").contains("14:40");
		}
	}

	@Test
	void twelveLetterNameFitsWithoutCutting() {
		String text = SmsTextBuilder.build(LONGEST, "Abcdefghijkl", LocalTime.of(14, 40));
		assertThat(text).startsWith("Abcdefghijkl ").hasSizeLessThanOrEqualTo(70);
	}

	@Test
	void templateTooLongEvenForOneLetterIsAnError() {
		assertThatThrownBy(() -> SmsTextBuilder.build("x".repeat(80) + "{name}", "Aryan", LocalTime.NOON))
			.isInstanceOf(IllegalArgumentException.class);
	}

}

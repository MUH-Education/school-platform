package com.muhjain.school.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameKeysTest {

	@Test
	void keyIgnoresCapitalLettersAndSpaces() {
		assertThat(NameKeys.key("hr 23 a 1104")).isEqualTo("HR23A1104");
		assertThat(NameKeys.key("  HR23A1104 ")).isEqualTo("HR23A1104");
		assertThat(NameKeys.key("Van 4")).isEqualTo(NameKeys.key("van4"));
		assertThat(NameKeys.key("Van 4")).isNotEqualTo(NameKeys.key("Van 14"));
	}

	@Test
	void tidyKeepsOneSpaceBetweenWords() {
		assertThat(NameKeys.tidy("  Van \t  4\n")).isEqualTo("Van 4");
		assertThat(NameKeys.tidy(null)).isNull();
	}

}

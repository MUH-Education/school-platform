package com.muhjain.school.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdMessagingCheckTest {

	private static MockEnvironment profile(String name) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(name);
		return environment;
	}

	@Test
	void prodRefusesToStartWithLogSender() {
		assertThatThrownBy(() -> new ProdMessagingCheck(profile("prod"), "log"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("sms-provider");
		assertThatThrownBy(() -> new ProdMessagingCheck(profile("prod"), " LOG ")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void prodStartsWithARealProvider() {
		assertThat(new ProdMessagingCheck(profile("prod"), "msg91")).isNotNull();
	}

	@Test
	void devAndTestMayUseTheLogSender() {
		assertThat(new ProdMessagingCheck(profile("dev"), "log")).isNotNull();
		assertThat(new ProdMessagingCheck(profile("test"), "log")).isNotNull();
	}

}

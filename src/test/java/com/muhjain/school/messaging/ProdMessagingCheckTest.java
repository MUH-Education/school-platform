package com.muhjain.school.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdMessagingCheckTest {

	@SuppressWarnings("unchecked")
	private static org.springframework.beans.factory.ObjectProvider<ProviderApi> provider(boolean present) {
		org.springframework.beans.factory.ObjectProvider<ProviderApi> provider = org.mockito.Mockito
			.mock(org.springframework.beans.factory.ObjectProvider.class);
		org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(present ? org.mockito.Mockito.mock(ProviderApi.class) : null);
		return provider;
	}

	private static MockEnvironment profile(String name) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(name);
		return environment;
	}

	@Test
	void prodRefusesToStartWithLogSender() {
		assertThatThrownBy(() -> new ProdMessagingCheck(profile("prod"), "log", provider(true)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("sms-provider");
		assertThatThrownBy(() -> new ProdMessagingCheck(profile("prod"), " LOG ", provider(true))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void prodStartsWithARealProvider() {
		assertThat(new ProdMessagingCheck(profile("prod"), "msg91", provider(true))).isNotNull();
	}

	@Test
	void prodRefusesToStartWithoutAProviderClass() {
		assertThatThrownBy(() -> new ProdMessagingCheck(profile("prod"), "msg91", provider(false)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("ProviderApi");
	}

	@Test
	void devAndTestMayUseTheLogSender() {
		assertThat(new ProdMessagingCheck(profile("dev"), "log", provider(false))).isNotNull();
		assertThat(new ProdMessagingCheck(profile("test"), "log", provider(false))).isNotNull();
	}

}

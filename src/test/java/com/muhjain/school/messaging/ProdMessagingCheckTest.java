package com.muhjain.school.messaging;

import com.muhjain.school.config.WebhookProperties;
import com.muhjain.school.service.messaging.ProdMessagingCheck;
import com.muhjain.school.service.messaging.ProviderApi;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdMessagingCheckTest {

	private static final WebhookProperties WEBHOOK = new WebhookProperties("a-webhook-secret-of-at-least-32-chars-x",
			null);

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
		assertThatThrownBy(() -> new ProdMessagingCheck(profile("prod"), "log", provider(true), WEBHOOK))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("sms-provider");
		assertThatThrownBy(() -> new ProdMessagingCheck(profile("prod"), " LOG ", provider(true), WEBHOOK)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void prodStartsWithARealProvider() {
		assertThat(new ProdMessagingCheck(profile("prod"), "msg91", provider(true), WEBHOOK)).isNotNull();
	}

	@Test
	void prodRefusesToStartWithoutAProviderClass() {
		assertThatThrownBy(() -> new ProdMessagingCheck(profile("prod"), "msg91", provider(false), WEBHOOK))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("ProviderApi");
	}

	@Test
	void localAndTestMayUseTheLogSender() {
		assertThat(new ProdMessagingCheck(profile("local"), "log", provider(false), WEBHOOK)).isNotNull();
		assertThat(new ProdMessagingCheck(profile("test"), "log", provider(false), WEBHOOK)).isNotNull();
	}

	@Test
	void prodNeedsTheWebhookSecret() {
		assertThatThrownBy(() -> new ProdMessagingCheck(profile("prod"), "msg91", provider(true),
				new WebhookProperties("", null))).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("APP_PROVIDER_WEBHOOK_SECRET");
		assertThatThrownBy(() -> new ProdMessagingCheck(profile("prod"), "msg91", provider(true),
				new WebhookProperties("short", null))).isInstanceOf(IllegalStateException.class);
		// Local and test may run without one: every webhook is then refused.
		assertThat(new ProdMessagingCheck(profile("local"), "log", provider(false), new WebhookProperties(null, null)))
			.isNotNull();
	}

}

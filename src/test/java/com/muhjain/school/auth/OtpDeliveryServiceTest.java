package com.muhjain.school.auth;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpDeliveryServiceTest {

	private static final String PHONE = "+919812340002";

	@Test
	void firstChannelThatWorksWins() throws Exception {
		FakeSender whatsapp = new FakeSender("WHATSAPP", true);
		FakeSender sms = new FakeSender("SMS", false);

		OtpDeliveryService delivery = new OtpDeliveryService(List.of(sms, whatsapp), properties("whatsapp", "sms"),
				env("dev"));

		assertThat(delivery.send(PHONE, "482913")).isEqualTo("SMS");
		assertThat(whatsapp.calls).containsExactly(PHONE);
		assertThat(sms.calls).containsExactly(PHONE);
	}

	@Test
	void everyChannelFailingThrows() {
		OtpDeliveryService delivery = new OtpDeliveryService(List.of(new FakeSender("SMS", true)), properties("sms"),
				env("dev"));

		assertThatThrownBy(() -> delivery.send(PHONE, "482913")).isInstanceOf(OtpSendException.class);
	}

	@Test
	void channelWithoutSenderStopsTheApp() {
		assertThatThrownBy(() -> new OtpDeliveryService(List.of(new FakeSender("LOG", false)),
				properties("whatsapp", "log"), env("dev")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("WHATSAPP");
	}

	@Test
	void prodWithOnlyLogChannelStopsTheApp() {
		assertThatThrownBy(() -> new OtpDeliveryService(List.of(new FakeSender("LOG", false)), properties("log"),
				env("prod")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("prod");
	}

	@Test
	void devWithOnlyLogChannelStarts() throws Exception {
		OtpDeliveryService delivery = new OtpDeliveryService(List.of(new FakeSender("LOG", false)), properties("log"),
				env("dev"));

		assertThat(delivery.send(PHONE, "482913")).isEqualTo("LOG");
	}

	private static OtpProperties properties(String... channels) {
		return new OtpProperties(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60), 5, 20, List.of(channels),
				"secret-for-the-delivery-service-test");
	}

	private static MockEnvironment env(String profile) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(profile);
		return environment;
	}

	static class FakeSender implements OtpSender {

		final String channel;

		final boolean fails;

		final List<String> calls = new ArrayList<>();

		FakeSender(String channel, boolean fails) {
			this.channel = channel;
			this.fails = fails;
		}

		@Override
		public String channel() {
			return channel;
		}

		@Override
		public void send(String phone, String code) throws OtpSendException {
			calls.add(phone);
			if (fails) {
				throw new OtpSendException(channel + " is down");
			}
		}

	}

}

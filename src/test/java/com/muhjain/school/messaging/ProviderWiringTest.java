package com.muhjain.school.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.muhjain.school.service.auth.OtpDeliveryService;
import com.muhjain.school.config.OtpProperties;
import com.muhjain.school.service.auth.impl.SmsOtpSender;
import com.muhjain.school.service.auth.impl.WhatsAppOtpSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.muhjain.school.config.ProviderProperties;
import com.muhjain.school.model.dto.response.SendResult;
import com.muhjain.school.exception.SmsSendException;
import com.muhjain.school.service.messaging.ProviderApi;
import com.muhjain.school.service.messaging.impl.ProviderSmsSender;

/** Everything except the provider's own HTTP calls: a fake {@link ProviderApi} stands in for the one class to write. */
class ProviderWiringTest {

	/** Records the calls. {@code whatsappDown} makes WhatsApp fail. */
	static class FakeApi implements ProviderApi {

		final List<String> calls = new ArrayList<>();

		boolean whatsappDown;

		@Override
		public String sendSms(String phone, String body, String templateId) {
			calls.add("sms:" + phone + ":" + templateId);
			return "ref-1";
		}

		@Override
		public void sendWhatsAppOtp(String phone, String code) throws SmsSendException {
			if (whatsappDown) {
				throw new SmsSendException("whatsapp timeout");
			}
			calls.add("wa:" + phone);
		}

		@Override
		public void sendSmsOtp(String phone, String code) {
			calls.add("otpsms:" + phone);
		}

		@Override
		public String sendWhatsAppTemplate(String phone, String providerTemplateId, Map<String, String> params,
				String mediaUrl) {
			calls.add("wa-template:" + phone + ":" + providerTemplateId);
			return "ref-2";
		}

		@Override
		public String sendWhatsAppGroupMessage(String groupId, String text, String mediaUrl) {
			calls.add("wa-group:" + groupId);
			return "ref-3";
		}

	}

	@SuppressWarnings("unchecked")
	private static ObjectProvider<ProviderApi> provider(ProviderApi api) {
		ObjectProvider<ProviderApi> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(api);
		return provider;
	}

	private static OtpDeliveryService delivery(ProviderApi api) {
		OtpProperties properties = mock(OtpProperties.class);
		when(properties.channels()).thenReturn(List.of("WHATSAPP", "SMS"));
		return new OtpDeliveryService(List.of(new WhatsAppOtpSender(provider(api)), new SmsOtpSender(provider(api))),
				properties, new MockEnvironment());
	}

	@Test
	void otpGoesByWhatsAppFirst() throws Exception {
		FakeApi api = new FakeApi();
		assertThat(delivery(api).send("+919811100001", "123456")).isEqualTo("WHATSAPP");
		assertThat(api.calls).containsExactly("wa:+919811100001");
	}

	@Test
	void otpFallsBackToSmsWhenWhatsAppFails() throws Exception {
		FakeApi api = new FakeApi();
		api.whatsappDown = true;
		assertThat(delivery(api).send("+919811100001", "123456")).isEqualTo("SMS");
		assertThat(api.calls).containsExactly("otpsms:+919811100001");
	}

	@Test
	void withoutAProviderClassTheOtpFailsInsteadOfPretending() {
		assertThatThrownBy(() -> delivery(null).send("+919811100001", "123456"))
			.isInstanceOf(com.muhjain.school.exception.OtpSendException.class);
	}

	@Test
	void parentSmsGoesToTheProviderWithItsTemplateId() throws Exception {
		FakeApi api = new FakeApi();
		SendResult result = new ProviderSmsSender(provider(api)).send("+919811100001", "text", "1107001");
		assertThat(result.providerRef()).isEqualTo("ref-1");
		assertThat(result.testOnly()).isFalse();
		assertThat(api.calls).containsExactly("sms:+919811100001:1107001");
	}

	@Test
	void parentSmsWithoutAProviderClassIsAFailureThatTheWorkerRetries() {
		assertThatThrownBy(() -> new ProviderSmsSender(provider(null)).send("+919811100001", "text", null))
			.isInstanceOf(SmsSendException.class);
	}

	@Test
	void providerSettingsHaveAFiveSecondTimeoutByDefault() {
		assertThat(new ProviderProperties("https://x", "k", "S", "t", "w", null).timeout())
			.isEqualTo(java.time.Duration.ofSeconds(5));
	}

}

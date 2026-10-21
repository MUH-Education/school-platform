package com.muhjain.school.messaging;

import com.muhjain.school.config.OtpProperties;
import com.muhjain.school.config.ProviderProperties;
import com.muhjain.school.exception.SmsSendException;
import com.muhjain.school.model.dto.response.SendResult;
import com.muhjain.school.util.MediaRules;
import com.muhjain.school.service.storage.FileStorage;
import com.muhjain.school.service.notification.impl.ProviderMediaPublisher;
import com.muhjain.school.service.messaging.impl.WhatsAppChannelSender;
import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.enums.MessageProblem;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.dto.response.SendContext;
import com.muhjain.school.service.auth.OtpDeliveryService;
import com.muhjain.school.service.auth.impl.SmsOtpSender;
import com.muhjain.school.service.auth.impl.WhatsAppOtpSender;
import com.muhjain.school.service.messaging.ProviderApi;
import com.muhjain.school.service.messaging.impl.ProviderSmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Everything except the provider's own HTTP calls: a fake {@link ProviderApi} stands in for the one class to write. */
class ProviderWiringTest {

	/** Records the calls. {@code whatsappDown} makes WhatsApp fail. */
	static class FakeApi implements ProviderApi {

		final List<String> calls = new ArrayList<>();

		boolean whatsappDown;

		boolean notOnWhatsApp;

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
		public String sendWhatsAppTemplate(String phone, String templateName, String language, List<String> params,
				String mediaId) throws SmsSendException {
			if (notOnWhatsApp) {
				throw new SmsSendException("not a WhatsApp number", MessageProblem.NOT_ON_WHATSAPP);
			}
			calls.add("wa-template:" + phone + ":" + templateName + ":" + language + ":" + params + ":" + mediaId);
			return "ref-2";
		}

		@Override
		public String sendWhatsAppText(String phone, String text, String mediaId) {
			calls.add("wa-text:" + phone + ":" + text);
			return "ref-3";
		}

		@Override
		public String uploadMedia(byte[] bytes, String contentType, String fileName) {
			calls.add("upload:" + contentType + ":" + fileName + ":" + bytes.length);
			return "media-1";
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

	// ---------------------------------------------------------------- Phase 11: WhatsApp and media

	private static MessageOutbox whatsAppRow(String body) {
		return new MessageOutbox(MessagePurpose.NOTICE, MessageChannel.WHATSAPP, "+919811100001", body,
				java.time.Instant.parse("2026-10-07T04:30:00Z"));
	}

	@Test
	void whatsAppTemplateGetsItsValuesInOrder() throws Exception {
		FakeApi api = new FakeApi();
		SendResult result = new WhatsAppChannelSender(provider(api)).send(whatsAppRow("text"),
				new SendContext(null, "open_day_oct", "hi", List.of("Rajesh Punia", "Aryan और Siya"), false, "m-9"));
		assertThat(result.providerRef()).isEqualTo("ref-2");
		assertThat(api.calls).containsExactly("wa-template:+919811100001:open_day_oct:hi:[Rajesh Punia, Aryan और Siya]:m-9");
	}

	@Test
	void freeTextGoesAsText() throws Exception {
		FakeApi api = new FakeApi();
		new WhatsAppChannelSender(provider(api)).send(whatsAppRow("Please collect the books."),
				new SendContext(null, null, null, List.of(), true, null));
		assertThat(api.calls).containsExactly("wa-text:+919811100001:Please collect the books.");
	}

	@Test
	void notOnWhatsAppIsToldToTheWorker() {
		FakeApi api = new FakeApi();
		api.notOnWhatsApp = true;
		assertThatThrownBy(() -> new WhatsAppChannelSender(provider(api)).send(whatsAppRow("x"),
				new SendContext(null, "t", "hi", List.of(), false, null)))
			.isInstanceOfSatisfying(SmsSendException.class,
					ex -> assertThat(ex.problem()).isEqualTo(MessageProblem.NOT_ON_WHATSAPP));
	}

	@Test
	void aTemplateWithoutAProviderNameIsAFailureNotAGuess() {
		assertThatThrownBy(() -> new WhatsAppChannelSender(provider(new FakeApi())).send(whatsAppRow("x"),
				new SendContext(null, null, "hi", List.of(), false, null)))
			.isInstanceOf(SmsSendException.class);
	}

	@Test
	void mediaIsUploadedFromOurStorageWithItsRandomName() throws Exception {
		FakeApi api = new FakeApi();
		FileStorage storage = mock(FileStorage.class);
		when(storage.open("notifications/9b1e.mp4")).thenReturn(new java.io.ByteArrayInputStream(new byte[] { 1, 2, 3 }));
		String id = new ProviderMediaPublisher(storage, provider(api)).publish("notifications/9b1e.mp4",
				MediaRules.Format.MP4);
		assertThat(id).isEqualTo("media-1");
		assertThat(api.calls).containsExactly("upload:video/mp4:9b1e.mp4:3");
	}

}

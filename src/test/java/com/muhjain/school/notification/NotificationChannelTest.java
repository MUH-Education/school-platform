package com.muhjain.school.notification;

import com.muhjain.school.exception.SmsSendException;
import com.muhjain.school.model.dto.request.NotificationRequest;
import com.muhjain.school.model.dto.request.SendNotificationRequest;
import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.dto.response.SendContext;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.*;
import com.muhjain.school.model.repository.NotificationRepository;
import com.muhjain.school.service.messaging.OutboxWorker;
import com.muhjain.school.service.messaging.impl.LogWhatsAppChannelSender;
import com.muhjain.school.service.notification.NotificationDispatcher;
import com.muhjain.school.service.notification.NotificationMediaService;
import com.muhjain.school.service.notification.NotificationService;
import com.muhjain.school.service.notification.impl.LogMediaPublisher;
import com.muhjain.school.service.storage.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Tasks 11.10 and 11.11: each row goes to the sender of its channel, a phone without WhatsApp gets the SMS text
 * instead, and a notification's file is given to the provider once. On the small school of
 * {@link NotificationTestBase}: Punia (+919811100001), Chauhan (+919811100002, stop list), Sharma (+919811100004).
 */
class NotificationChannelTest extends NotificationTestBase {

	@MockitoSpyBean
	private LogWhatsAppChannelSender whatsApp;

	@MockitoSpyBean
	private LogMediaPublisher mediaPublisher;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationDispatcher dispatcher;

	@Autowired
	private OutboxWorker worker;

	@Autowired
	private NotificationMediaService mediaService;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private FileStorage storage;

	private AppUser owner;

	private String video;

	@BeforeEach
	void ownerAndVideo() throws IOException {
		owner = addUser("+919812340001", Role.OWNER);
		video = storage.save("notifications", MediaFiles.mp4("isom", 1000, 45_000), "mp4");
	}

	@AfterEach
	void removeVideo() throws IOException {
		storage.delete(video);
	}

	private long send(NotificationKind kind, String body, String smsBody, String header, AudienceGroup group) {
		long templateId = jdbc.queryForObject("insert into message_template (kind, name, channel, body, approved_body, "
				+ "sms_body, approval, provider_template_name, provider_language, header_type, media_key) "
				+ "values (?, 'T', 'WHATSAPP', ?, ?, ?, 'APPROVED', 'open_day_oct', 'hi', ?, ?) returning id", Long.class,
				TemplateKind.of(kind).name(), body, body, smsBody, header, "NONE".equals(header) ? null : video);
		long id = notificationService.create(new NotificationRequest("Test", kind, templateId, null, null, Map.of(),
				null, new Audience(List.of(group), null)), Role.OWNER.permissions(), owner.getId()).id();
		notificationService.send(id, new SendNotificationRequest(true, null), Role.OWNER.permissions(), owner.getId());
		return id;
	}

	private Map<String, Object> row(long notificationId, String phone, String channel) {
		return jdbc.queryForMap("select id, status, problem, attempts, fallback_of_id, body from message_outbox "
				+ "where notification_id = ? and phone = ? and channel = ?", notificationId, phone, channel);
	}

	@Test
	void outboxWorkerRoutesWhatsAppRowsToWhatsAppSender() throws Exception {
		long id = send(NotificationKind.NOTICE, "नमस्ते {Parent name}, {Child name} की PTM शनिवार को।", null, "NONE",
				AudienceGroup.CURRENT_PARENTS);
		dispatcher.runOnce();
		worker.runOnce();

		ArgumentCaptor<SendContext> context = ArgumentCaptor.forClass(SendContext.class);
		verify(whatsApp, times(3)).send(any(), context.capture());
		// The template's name and its values in the order of its {boxes}: {{1}} = Parent name, {{2}} = Child name.
		assertThat(context.getAllValues()).anySatisfy(c -> {
			assertThat(c.templateName()).isEqualTo("open_day_oct");
			assertThat(c.language()).isEqualTo("hi");
			assertThat(c.params()).containsExactly("Rajesh Punia", "Aryan और Siya");
			assertThat(c.freeText()).isFalse();
		});
		assertThat(row(id, "+919811100001", "WHATSAPP")).containsEntry("status", "TEST_ONLY");
	}

	@Test
	void notOnWhatsAppGetsTheSmsTextInstead() throws Exception {
		doThrow(new SmsSendException("not a WhatsApp number", MessageProblem.NOT_ON_WHATSAPP)).when(whatsApp)
			.send(argThat(r -> r != null && "+919811100004".equals(r.getPhone())), any());
		long id = send(NotificationKind.NOTICE, "{Child name} की PTM शनिवार को।", "{Child name}: PTM शनिवार। MUH Jain School",
				"NONE", AudienceGroup.CURRENT_PARENTS);
		dispatcher.runOnce();
		worker.runOnce();

		Map<String, Object> failed = row(id, "+919811100004", "WHATSAPP");
		assertThat(failed).containsEntry("status", "FAILED")
			.containsEntry("problem", "NOT_ON_WHATSAPP")
			.containsEntry("attempts", 1);   // no second WhatsApp try
		Map<String, Object> sms = row(id, "+919811100004", "SMS");
		assertThat(sms).containsEntry("status", "QUEUED")
			.containsEntry("fallback_of_id", failed.get("id"))
			.containsEntry("body", "Dev: PTM शनिवार। MUH Jain School");

		worker.runOnce();
		assertThat(row(id, "+919811100004", "SMS")).containsEntry("status", "TEST_ONLY");
		// The others got WhatsApp; no SMS for them.
		assertThat(jdbc.queryForObject("select count(*) from message_outbox where notification_id = ? "
				+ "and channel = 'SMS'", Integer.class, id)).isEqualTo(1);
		// The notification closes as SENT: something went.
		dispatcher.runOnce();
		assertThat(notifications.findById(id).orElseThrow().getStatus()).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	void noSmsTextMeansNoSmsCopy() throws Exception {
		doThrow(new SmsSendException("not a WhatsApp number", MessageProblem.NOT_ON_WHATSAPP)).when(whatsApp)
			.send(any(), any());
		long id = send(NotificationKind.NOTICE, "PTM शनिवार को।", null, "NONE", AudienceGroup.CURRENT_PARENTS);
		dispatcher.runOnce();
		worker.runOnce();
		assertThat(jdbc.queryForObject("select count(*) from message_outbox where notification_id = ? "
				+ "and channel = 'SMS'", Integer.class, id)).isZero();
		dispatcher.runOnce();
		assertThat(notifications.findById(id).orElseThrow().getStatus()).isEqualTo(NotificationStatus.FAILED);
	}

	@Test
	void mediaPublishedOncePerNotification() throws Exception {
		long id = send(NotificationKind.GREETING, "Happy Diwali {Parent name} जी", null, "VIDEO",
				AudienceGroup.CURRENT_PARENTS);
		dispatcher.runOnce();
		verify(mediaPublisher, times(1)).publish(eq(video), any());
		Notification n = notifications.findById(id).orElseThrow();
		assertThat(n.getProviderMediaId()).startsWith("log-");
		assertThat(n.getProviderMediaAt()).isEqualTo(clock.instant());

		// Every WhatsApp row points at the same uploaded file.
		worker.runOnce();
		ArgumentCaptor<SendContext> context = ArgumentCaptor.forClass(SendContext.class);
		verify(whatsApp, times(2)).send(any(), context.capture());   // Chauhan is stopped
		assertThat(context.getAllValues()).extracting(SendContext::mediaId).containsOnly(n.getProviderMediaId());

		// Asked again within 25 days: no new upload. After 25 days: uploaded again.
		mediaService.ensurePublished(n);
		verify(mediaPublisher, times(1)).publish(any(), any());
		clock.setInstant(clock.instant().plus(Duration.ofDays(26)));
		mediaService.ensurePublished(n);
		verify(mediaPublisher, times(2)).publish(any(), any());
	}

	@Test
	void aFileTheProviderRefusesMakesTheNotificationWait() throws Exception {
		doThrow(new SmsSendException("provider down")).when(mediaPublisher).publish(any(), any());
		long id = send(NotificationKind.GREETING, "Happy Diwali", null, "VIDEO", AudienceGroup.CURRENT_PARENTS);
		dispatcher.runOnce();
		Notification waiting = notifications.findById(id).orElseThrow();
		assertThat(waiting.getStatus()).isEqualTo(NotificationStatus.SENDING);
		assertThat(waiting.getExpandedAt()).isNull();
		assertThat(jdbc.queryForObject("select count(*) from message_outbox", Integer.class)).isZero();

		doCallRealMethod().when(mediaPublisher).publish(any(), any());
		dispatcher.runOnce();
		assertThat(notifications.findById(id).orElseThrow().getExpandedAt()).isNotNull();
	}

	@Test
	void busSmsStillGoesToTheSmsSender() {
		// A bus SMS row (no notification) never touches WhatsApp.
		jdbc.update("insert into message_outbox (purpose, channel, phone, body, next_try_at) values "
				+ "('OTHER', 'SMS', '+919811100001', 'x', ?)", java.sql.Timestamp.from(clock.instant()));
		worker.runOnce();
		verify(whatsApp, never()).send(any(), any());
		assertThat(jdbc.queryForObject("select status from message_outbox", String.class)).isEqualTo("TEST_ONLY");
	}

}

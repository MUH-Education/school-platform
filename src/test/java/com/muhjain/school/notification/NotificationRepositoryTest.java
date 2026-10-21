package com.muhjain.school.notification;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.entity.*;
import com.muhjain.school.model.enums.*;
import com.muhjain.school.model.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** The Phase 11 entities read and write their columns, and the two dispatcher queries lock correctly. */
class NotificationRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private MessageTemplateRepository templates;

	@Autowired
	private MessageTemplateButtonRepository buttons;

	@Autowired
	private MessageOutboxRepository outbox;

	@Autowired
	private MessageStopRepository stops;

	@Autowired
	private InboundMessageRepository inbound;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private MessageTemplate template(TemplateKind kind) {
		MessageTemplate template = new MessageTemplate(kind, "Open day", "नमस्ते {Parent name}", anyUserId());
		template.setExampleValues(Map.of("Parent name", "Rajesh Punia"));
		template.setApproval(TemplateApproval.APPROVED, null, anyUserId());
		return templates.save(template);
	}

	private Notification notification(NotificationStatus status, Instant scheduledAt) {
		Notification n = new Notification();
		n.setKind(NotificationKind.NOTICE);
		n.setName("PTM on Saturday");
		n.setTemplateId(template(TemplateKind.NOTICE).getId());
		n.setBody("नमस्ते {Parent name}");
		n.setAudience(new Audience(List.of(AudienceGroup.CURRENT_PARENTS), null));
		n.setStatus(status);
		n.setScheduledAt(scheduledAt);
		n.setCreatedBy(anyUserId());
		return notifications.save(n);
	}

	@Test
	void notificationKeepsItsAudienceAsJson() {
		Notification n = new Notification();
		n.setKind(NotificationKind.FEE_REMINDER);
		n.setName("Fee reminder, October");
		n.setTemplateId(template(TemplateKind.FEE_REMINDER).getId());
		n.setBody("{Parent name} जी, {Months} की फ़ीस {Amount due} बाकी है।");
		n.setFixedValues(Map.of("Pay by", "15 अक्टूबर"));
		n.setMediaKey("notifications/a.pdf");
		n.setMediaType(MediaFileType.DOCUMENT);
		n.setAudience(new Audience(List.of(AudienceGroup.FAMILIES_WITH_DUES),
				new AudienceFilter(List.of("10"), List.of(4L), null, FeeStatus.DELAYED, 2, new BigDecimal("1500.00"), 7,
						true, null, null)));
		n.setCreatedBy(anyUserId());
		long id = notifications.save(n).getId();

		Notification read = notifications.findById(id).orElseThrow();
		assertThat(read.getStatus()).isEqualTo(NotificationStatus.DRAFT);
		assertThat(read.getSource()).isEqualTo(NotificationSource.MANUAL);
		assertThat(read.getFixedValues()).containsEntry("Pay by", "15 अक्टूबर");
		assertThat(read.getAudience().groups()).containsExactly(AudienceGroup.FAMILIES_WITH_DUES);
		AudienceFilter filter = read.getAudience().filter();
		assertThat(filter.classes()).containsExactly("10");
		assertThat(filter.routeIds()).containsExactly(4L);
		assertThat(filter.feeStatus()).isEqualTo(FeeStatus.DELAYED);
		assertThat(filter.minMonthsLate()).isEqualTo(2);
		assertThat(filter.minAmount()).isEqualByComparingTo("1500");
		assertThat(filter.skipPromised()).isTrue();
		// Empty lists, never null, so a resolver does not need null checks.
		assertThat(filter.familyIds()).isEmpty();
		assertThat(jdbc.queryForObject("select audience ->> 'groups' from notification where id = ?", String.class, id))
			.isEqualTo("[\"FAMILIES_WITH_DUES\"]");
	}

	@Test
	void notificationTemplateWithButtonsAndExamples() {
		MessageTemplate saved = template(TemplateKind.MARKETING);
		MessageTemplateButton stop = new MessageTemplateButton();
		stop.setTemplateId(saved.getId());
		stop.setSeqNo(1);
		stop.setType(ButtonType.REPLY);
		stop.setText("Stop messages");
		stop.setAction(ButtonAction.STOP_MESSAGES.name());
		buttons.save(stop);

		MessageTemplate read = templates.findById(saved.getId()).orElseThrow();
		assertThat(read.getKind()).isEqualTo(TemplateKind.MARKETING);
		assertThat(read.getCode()).isNull();
		assertThat(read.getChannel()).isEqualTo(MessageChannel.WHATSAPP);
		assertThat(read.getExampleValues()).containsEntry("Parent name", "Rajesh Punia");
		assertThat(templates.findByKindAndActiveTrueOrderByNameAsc(TemplateKind.MARKETING)).hasSize(1);
		// The Phase 5 texts are not notification templates.
		assertThat(templates.findByKindNotAndActiveTrueOrderByNameAsc(TemplateKind.SYSTEM))
			.extracting(MessageTemplate::getId)
			.containsExactly(saved.getId());
		assertThat(buttons.findByTemplateIdOrderBySeqNoAsc(saved.getId())).extracting(MessageTemplateButton::getText)
			.containsExactly("Stop messages");

		// A reason is kept only for REJECTED.
		read.setApproval(TemplateApproval.REJECTED, "Too promotional", anyUserId());
		templates.saveAndFlush(read);
		read.setApproval(TemplateApproval.APPROVED, "ignored", anyUserId());
		templates.saveAndFlush(read);
		assertThat(templates.findById(saved.getId()).orElseThrow().getRejectionReason()).isNull();
	}

	@Test
	void outboxRowOfANotificationKeepsValuesAndSkipReason() {
		Notification n = notification(NotificationStatus.SENDING, null);
		MessageOutbox row = new MessageOutbox(MessagePurpose.NOTICE, MessageChannel.WHATSAPP, "+919811100001",
				"नमस्ते Rajesh Punia", Instant.now(clock));
		row.setNotificationId(n.getId());
		row.setTemplateValues(Map.of("Parent name", "Rajesh Punia"));
		row.markSkipped(SkipReason.ALREADY_PAID);
		long id = outbox.save(row).getId();

		MessageOutbox read = outbox.findById(id).orElseThrow();
		assertThat(read.getStatus()).isEqualTo(MessageStatus.SKIPPED);
		assertThat(read.getSkipReason()).isEqualTo(SkipReason.ALREADY_PAID);
		assertThat(read.getTemplateValues()).containsEntry("Parent name", "Rajesh Punia");
		assertThat(read.getNotificationId()).isEqualTo(n.getId());
		// The worker never picks a SKIPPED row.
		List<MessageOutbox> claimed = new TransactionTemplate(transactionManager).execute(s -> outbox.claimQueued(50));
		assertThat(claimed).isEmpty();
	}

	@Test
	void claimDueTakesOnlyScheduledRowsWhoseTimeHasCome() {
		Instant now = Instant.now(clock);
		Notification due = notification(NotificationStatus.SCHEDULED, now.minus(Duration.ofMinutes(1)));
		notification(NotificationStatus.SCHEDULED, now.plus(Duration.ofHours(1)));
		notification(NotificationStatus.DRAFT, null);
		Notification sendNow = notification(NotificationStatus.SENDING, null);
		Notification expanded = notification(NotificationStatus.SENDING, null);
		expanded.setExpandedAt(now);
		notifications.save(expanded);

		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		List<Notification> dueNow = tx.execute(s -> notifications.claimDue(now, 10));
		assertThat(dueNow).extracting(Notification::getId).containsExactly(due.getId());
		List<Notification> notExpanded = tx.execute(s -> notifications.findSendingWithoutExpansion(10));
		assertThat(notExpanded).extracting(Notification::getId).containsExactly(sendNow.getId());
	}

	@Test
	void aSecondDispatcherSkipsALockedNotification() throws Exception {
		Instant now = Instant.now(clock);
		notification(NotificationStatus.SCHEDULED, now.minus(Duration.ofMinutes(1)));
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			// App instance 1 claims the row and keeps its transaction open.
			Future<Integer> first = pool.submit(() -> tx.execute(s -> {
				int claimed = notifications.claimDue(now, 10).size();
				locked.countDown();
				try {
					release.await(10, TimeUnit.SECONDS);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				return claimed;
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			// App instance 2, at the same moment: the row is skipped, not waited for.
			List<Notification> second = tx.execute(s -> notifications.claimDue(now, 10));
			assertThat(second).isEmpty();
			release.countDown();
			assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(1);
		}
		finally {
			release.countDown();
			pool.shutdownNow();
		}
	}

	@Test
	void stopListAndInboundMessages() {
		long guardian = jdbc.queryForObject(
				"insert into guardian (name, phone) values ('Rajesh Punia', '+919811100001') returning id", Long.class);
		MessageStop stop = new MessageStop();
		stop.setGuardianId(guardian);
		stop.setReason(StopReason.WROTE_STOP);
		stops.save(stop);
		assertThat(stops.existsByGuardianId(guardian)).isTrue();
		assertThat(stops.findByGuardianIdIn(List.of(guardian, -1L))).hasSize(1);

		InboundMessage message = new InboundMessage();
		message.setPhone("+919811100001");
		message.setGuardianId(guardian);
		message.setText("शुक्रवार तक जमा कर देंगे");
		message.setProviderRef("wamid.1");
		message.setReceivedAt(Instant.now(clock));
		message.setPromisedUntil(LocalDate.of(2026, 10, 23));
		inbound.save(message);
		InboundMessage read = inbound.findByProviderRef("wamid.1").orElseThrow();
		assertThat(read.isHandled()).isFalse();
		assertThat(read.getPromisedUntil()).isEqualTo(LocalDate.of(2026, 10, 23));
	}

}

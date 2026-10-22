package com.muhjain.school.service.dev;

import com.muhjain.school.model.dto.request.HandleInboundRequest;
import com.muhjain.school.model.dto.request.InboundWebhookRequest;
import com.muhjain.school.model.dto.request.NotificationRequest;
import com.muhjain.school.model.dto.request.NotificationTemplateRequest;
import com.muhjain.school.model.dto.request.SendNotificationRequest;
import com.muhjain.school.model.dto.request.StatusWebhookRequest;
import com.muhjain.school.model.dto.request.TemplateApprovalRequest;
import com.muhjain.school.model.dto.request.TemplateButtonRequest;
import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.FamilyContact;
import com.muhjain.school.model.dto.response.NotificationRecipientResponse;
import com.muhjain.school.model.dto.response.StudentReportRow;
import com.muhjain.school.model.enums.*;
import com.muhjain.school.service.messaging.DeliveryStatusService;
import com.muhjain.school.service.messaging.OutboxWorker;
import com.muhjain.school.service.notification.InboundMessageService;
import com.muhjain.school.service.notification.NotificationDispatcher;
import com.muhjain.school.service.notification.NotificationService;
import com.muhjain.school.service.notification.NotificationTemplateService;
import com.muhjain.school.service.notification.StopListService;
import com.muhjain.school.service.storage.FileStorage;
import com.muhjain.school.service.student.GuardianService;
import com.muhjain.school.service.student.StudentQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 11 dev data (task 11.17), called at the end of {@link DevDataLoader}. Loaded once, when there is no
 * notification template yet. Uses services only, as the office would:
 * <ul>
 * <li>12 templates: 3 NOTICE, 1 URGENT, 3 MARKETING (open day with a poster, approved; admissions WAITING; summer
 * camp REJECTED with a reason), 1 GREETING with a video, 3 FEE_REMINDER tones (gentle, firm, final), 1 STAFF.</li>
 * <li>3 families on the stop list (2 by the button, 1 by a "STOP" reply).</li>
 * <li>"Open day" (MARKETING) sent to every parent: one row per family, in mixed states — read, delivered, test
 * mode, failed (phone off), not on WhatsApp (with its SMS copy), and the stopped families SKIPPED.</li>
 * <li>4 replies: STOP, "will pay by Friday" (handled, promised), "Is the bus late?" (waiting), "Book a visit" tap
 * on the open-day message (waiting).</li>
 * <li>A NOTICE scheduled for the next school day at 10:00 and a FEE_REMINDER draft.</li>
 * </ul>
 * Started inside quiet hours (21:00 to 08:00), the open-day rows wait for the morning and stay QUEUED.
 * The poster is a real PNG; the "video" is a tiny MP4 header only (enough for the screens, not for a player).
 */
@Component
@Profile("local")
public class NotificationDevData {

	private static final Logger log = LoggerFactory.getLogger(NotificationDevData.class);

	private final NotificationTemplateService templates;

	private final NotificationService notifications;

	private final NotificationDispatcher dispatcher;

	private final OutboxWorker worker;

	private final DeliveryStatusService deliveryStatus;

	private final InboundMessageService inbound;

	private final StopListService stopList;

	private final StudentQueryService studentQuery;

	private final GuardianService guardianService;

	private final FileStorage storage;

	private final Clock clock;

	// The office user of this load (the owner), the author of every template.
	private Long actor;

	public NotificationDevData(NotificationTemplateService templates, NotificationService notifications,
			NotificationDispatcher dispatcher, OutboxWorker worker, DeliveryStatusService deliveryStatus,
			InboundMessageService inbound, StopListService stopList, StudentQueryService studentQuery,
			GuardianService guardianService, FileStorage storage, Clock clock) {
		this.templates = templates;
		this.notifications = notifications;
		this.dispatcher = dispatcher;
		this.worker = worker;
		this.deliveryStatus = deliveryStatus;
		this.inbound = inbound;
		this.stopList = stopList;
		this.studentQuery = studentQuery;
		this.guardianService = guardianService;
		this.storage = storage;
		this.clock = clock;
	}

	/** @param userId the office user who "does" all of it (the owner) */
	public void load(Long userId) {
		if (userId == null) {
			log.info("Dev data: no office user, notifications not loaded");
			return;
		}
		if (!templates.list(null).isEmpty()) {
			log.info("Dev data: there are notification templates already, notifications not loaded");
			return;
		}
		List<FamilyContact> families = families();
		if (families.size() < 6) {
			log.info("Dev data: too few families, notifications not loaded");
			return;
		}
		Set<Permission> owner = Role.OWNER.permissions();
		this.actor = userId;
		String poster = save(poster(), "png");
		String video = save(tinyMp4(), "mp4");

		// ---------------------------------------------------------------- templates
		Map<String, String> ptmExamples = Map.of("Parent name", "Rajesh Punia", "Child name", "Aryan", "Date",
				"10 अक्टूबर");
		long ptm = approved(template("PTM", NotificationKind.NOTICE,
				"नमस्ते {Parent name} जी, {Child name} की PTM {Date} को सुबह 10 बजे स्कूल में है। — MUH Jain School",
				"{Child name}: PTM {Date} सुबह 10 बजे। MUH Jain School", HeaderType.NONE, null, List.of(), ptmExamples),
				userId);
		approved(template("School closed", NotificationKind.NOTICE, "{Date} को स्कूल बंद रहेगा। — MUH Jain School",
				"{Date} को स्कूल बंद। MUH Jain School", HeaderType.NONE, null, List.of(), Map.of("Date", "20 अक्टूबर")),
				userId);
		approved(template("Exam timetable", NotificationKind.NOTICE,
				"{Child name} की परीक्षा {Date} से शुरू होगी। समय-सारणी डायरी में है। — MUH Jain School", null,
				HeaderType.NONE, null, List.of(), Map.of("Child name", "Aryan", "Date", "2 नवंबर")), userId);
		approved(template("Bus late", NotificationKind.URGENT,
				"{Route} की बस आज लगभग {Minutes} मिनट देर से आएगी। — MUH Jain School",
				"{Route} बस {Minutes} मिनट देर। MUH Jain School", HeaderType.NONE, null, List.of(),
				Map.of("Route", "Route 4", "Minutes", "20")), userId);
		long openDay = approved(template("Open day", NotificationKind.MARKETING,
				"नमस्ते {Parent name} जी, रविवार 25 अक्टूबर को MUH Jain Global School में open day है। सुबह 10 से 1 बजे तक आएँ।",
				"Open day रविवार 25 अक्टूबर, 10-1 बजे। MUH Jain School", HeaderType.IMAGE, poster,
				List.of(new TemplateButtonRequest(ButtonType.CALL, "Call school", "9800000100"),
						new TemplateButtonRequest(ButtonType.REPLY, "Book a visit", "BOOK_VISIT")),
				Map.of("Parent name", "Rajesh Punia")), userId);
		template("Admissions 2027-28", NotificationKind.MARKETING,
				"नमस्ते {Parent name} जी, सत्र 2027-28 के लिए admission शुरू हैं। — MUH Jain Global School", null,
				HeaderType.NONE, null, List.of(), Map.of("Parent name", "Rajesh Punia"), userId);
		long camp = template("Summer camp", NotificationKind.MARKETING,
				"Summer camp में आज ही नाम लिखवाएँ! — MUH Jain Global School", null, HeaderType.NONE, null, List.of(),
				Map.of(), userId);
		templates.decide(camp, new TemplateApprovalRequest(TemplateApproval.REJECTED,
				"Too promotional for this category; send it as MARKETING with an opt-out."), userId);
		approved(template("Diwali wishes", NotificationKind.GREETING,
				"{Parent name} जी, आपको और आपके परिवार को दीपावली की शुभकामनाएँ। — MUH Jain Global School", null,
				HeaderType.VIDEO, video, List.of(), Map.of("Parent name", "Rajesh Punia")), userId);
		Map<String, String> feeExamples = Map.of("Parent name", "Rajesh Punia", "Child name", "Aryan", "Months",
				"सितंबर और अक्टूबर", "Amount due", "₹5,000");
		long gentle = approved(template("Fee reminder, gentle", NotificationKind.FEE_REMINDER,
				"नमस्ते {Parent name} जी, {Child name} की {Months} की फ़ीस {Amount due} बाकी है। सुविधा अनुसार जमा करें। धन्यवाद।",
				"{Child name} की फ़ीस {Amount due} बाकी। MUH Jain School", HeaderType.NONE, null, List.of(), feeExamples),
				userId);
		approved(template("Fee reminder, firm", NotificationKind.FEE_REMINDER,
				"{Parent name} जी, {Child name} की {Months} की फ़ीस {Amount due} अभी भी बाकी है। कृपया इस हफ़्ते जमा करें।",
				"फ़ीस {Amount due} बाकी, इस हफ़्ते जमा करें। MUH Jain School", HeaderType.NONE, null, List.of(),
				feeExamples), userId);
		approved(template("Fee reminder, final", NotificationKind.FEE_REMINDER,
				"{Parent name} जी, {Child name} की {Months} की फ़ीस {Amount due} बाकी है। कृपया आज office से मिलें।",
				"फ़ीस {Amount due} बाकी, आज office आएँ। MUH Jain School", HeaderType.NONE, null, List.of(), feeExamples),
				userId);
		approved(template("Staff meeting", NotificationKind.STAFF,
				"{Staff name} जी, staff meeting {Date} को दोपहर 3 बजे staff room में है।", null, HeaderType.NONE, null,
				List.of(), Map.of("Staff name", "Sunita", "Date", "शनिवार")), userId);

		// ---------------------------------------------------------------- stop list
		stopList.add(families.get(0).guardianId(), StopReason.TAPPED_BUTTON, null);
		stopList.add(families.get(1).guardianId(), StopReason.TAPPED_BUTTON, null);
		reply("dev.in.1", families.get(2).phone(), "STOP", null, null);

		// ---------------------------------------------------------------- "Open day", sent
		long sent = notifications.create(new NotificationRequest("Open day, Sunday 25 October",
				NotificationKind.MARKETING, openDay, null, null, Map.of(), null,
				new Audience(List.of(AudienceGroup.CURRENT_PARENTS), null)), owner, userId).id();
		notifications.send(sent, new SendNotificationRequest(true, null), owner, userId);
		dispatcher.runOnce();
		worker.runOnce();
		List<NotificationRecipientResponse> rows = notifications.recipients(sent, 0, 100).items();
		mixStates(rows);
		// The family that taps "Book a visit" read the message first.
		FamilyContact visitor = families.get(5);
		String tapped = rows.stream()
			.filter(r -> visitor.guardianId().equals(r.personId()) && r.outboxId() != null)
			.map(r -> "log-" + r.outboxId())
			.findFirst()
			.orElse(null);
		if (tapped != null) {
			status(tapped, DeliveryEvent.READ, OffsetDateTime.now(clock).minusMinutes(5), null);
		}
		worker.runOnce();   // the SMS copy of "not on WhatsApp"
		dispatcher.runOnce();   // closes it when nothing is QUEUED

		// ---------------------------------------------------------------- replies
		FamilyContact payer = families.get(3);
		reply("dev.in.2", payer.phone(), "शुक्रवार तक फ़ीस जमा कर देंगे", null, null);
		inbound.idByProviderRef("dev.in.2").ifPresent(id -> inbound.handle(id, new HandleInboundRequest(
				"Called back: will pay on Friday.", nextFriday(), false, null), owner, userId));
		reply("dev.in.3", families.get(4).phone(), "आज बस लेट है क्या?", null, null);
		if (tapped != null) {
			reply("dev.in.4", visitor.phone(), null, ButtonAction.BOOK_VISIT, tapped);
		}

		// ---------------------------------------------------------------- scheduled and draft
		long scheduled = notifications.create(new NotificationRequest("PTM Class 10", NotificationKind.NOTICE, ptm, null,
				null, Map.of("Date", "शनिवार"), null, new Audience(List.of(AudienceGroup.CURRENT_PARENTS),
						new AudienceFilter(List.of("10"), null, null, null, null, null, null, null, null, null))),
				owner, userId).id();
		notifications.send(scheduled, new SendNotificationRequest(null, nextSchoolDayAt10()), owner, userId);
		notifications.create(new NotificationRequest("Fee reminder, October", NotificationKind.FEE_REMINDER, gentle,
				null, null, Map.of(), null, new Audience(List.of(AudienceGroup.FAMILIES_WITH_DUES), null)), owner,
				userId);
		log.info("Dev data: notifications loaded (12 templates, 3 stops, open day sent to {} families, 4 replies)",
				rows.size());
	}

	// Read, delivered, failed (phone off), not on WhatsApp, the rest as sent in test mode.
	private void mixStates(List<NotificationRecipientResponse> rows) {
		int i = 0;
		for (NotificationRecipientResponse row : rows) {
			if (row.status() != MessageStatus.TEST_ONLY || row.outboxId() == null) {
				continue;   // skipped (stop list), or held by quiet hours
			}
			String ref = "log-" + row.outboxId();
			OffsetDateTime at = OffsetDateTime.now(clock).minusMinutes(30 - Math.min(i, 29));
			switch (i % 6) {
				case 0, 1 -> status(ref, DeliveryEvent.READ, at, null);
				case 2 -> status(ref, DeliveryEvent.DELIVERED, at, null);
				case 3 -> {
					if (i == 3) {
						status(ref, DeliveryEvent.FAILED, at, "PHONE_OFF");
					}
				}
				case 4 -> {
					if (i == 4) {
						status(ref, DeliveryEvent.FAILED, at, "NOT_ON_WHATSAPP");
					}
				}
				default -> {
					// left as it is: TEST_ONLY
				}
			}
			i++;
		}
	}

	private void status(String ref, DeliveryEvent event, OffsetDateTime at, String problem) {
		deliveryStatus.apply(new StatusWebhookRequest(ref, event, at, problem));
	}

	private void reply(String ref, String phone, String text, ButtonAction button, String inReplyTo) {
		inbound.receive(new InboundWebhookRequest(ref, phone, text, button, inReplyTo, OffsetDateTime.now(clock)));
	}

	private long template(String name, NotificationKind kind, String body, String sms, HeaderType header,
			String mediaKey, List<TemplateButtonRequest> buttons, Map<String, String> examples, Long userId) {
		return templates.create(new NotificationTemplateRequest(name, kind, "hi", body, sms, header, mediaKey, buttons,
				examples), userId).id();
	}

	private long template(String name, NotificationKind kind, String body, String sms, HeaderType header,
			String mediaKey, List<TemplateButtonRequest> buttons, Map<String, String> examples) {
		return template(name, kind, body, sms, header, mediaKey, buttons, examples, actor);
	}

	private long approved(long templateId, Long userId) {
		templates.decide(templateId, new TemplateApprovalRequest(TemplateApproval.APPROVED, null), userId);
		return templateId;
	}

	// One contact per family (the primary phone of ACTIVE children), oldest family first.
	private List<FamilyContact> families() {
		List<Long> children = studentQuery.activeMatching(null, null, null, null, null, LocalDate.now(clock))
			.stream()
			.map(StudentReportRow::id)
			.toList();
		List<FamilyContact> result = new ArrayList<>(guardianService.familyContacts(children)
			.values()
			.stream()
			.filter(c -> c.phone() != null)
			.distinct()
			.sorted(Comparator.comparing(FamilyContact::guardianId))
			.toList());
		return result;
	}

	private LocalDate nextFriday() {
		LocalDate day = LocalDate.now(clock).plusDays(1);
		while (day.getDayOfWeek() != DayOfWeek.FRIDAY) {
			day = day.plusDays(1);
		}
		return day;
	}

	private OffsetDateTime nextSchoolDayAt10() {
		LocalDate day = LocalDate.now(clock).plusDays(1);
		if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
			day = day.plusDays(1);
		}
		return day.atTime(LocalTime.of(10, 0)).atZone(clock.getZone()).toOffsetDateTime();
	}

	private String save(byte[] bytes, String extension) {
		try {
			return storage.save("notifications", bytes, extension);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	// A simple 1080 x 1350 poster (WhatsApp's 4:5).
	private static byte[] poster() {
		BufferedImage image = new BufferedImage(1080, 1350, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(0x1F, 0x4E, 0x79));
		g.fillRect(0, 0, 1080, 1350);
		g.setColor(Color.WHITE);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 96));
		g.drawString("OPEN DAY", 290, 560);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 48));
		g.drawString("Sunday 25 October, 10 am - 1 pm", 190, 680);
		g.drawString("MUH Jain Global School, Tohana", 200, 760);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", out);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return out.toByteArray();
	}

	// "ftyp" + "moov/mvhd" (45 seconds): an MP4 header, enough for the type check and the length.
	private static byte[] tinyMp4() {
		ByteBuffer ftyp = ByteBuffer.allocate(16);
		ftyp.putInt(16).put(ascii("ftyp")).put(ascii("isom")).putInt(0);
		ByteBuffer moov = ByteBuffer.allocate(116);
		moov.putInt(116).put(ascii("moov")).putInt(108).put(ascii("mvhd")).putInt(0).putInt(0).putInt(0).putInt(1000)
			.putInt(45_000);
		ByteBuffer all = ByteBuffer.allocate(132);
		all.put(ftyp.array()).put(moov.array());
		return all.array();
	}

	private static byte[] ascii(String text) {
		return text.getBytes(StandardCharsets.US_ASCII);
	}

}

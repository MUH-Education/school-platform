package com.muhjain.school.notification;

import com.muhjain.school.model.dto.request.PaymentRequest;
import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.GuardOutcome;
import com.muhjain.school.model.dto.response.PaymentLine;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.entity.MessageTemplate;
import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.*;
import com.muhjain.school.model.repository.MessageOutboxRepository;
import com.muhjain.school.model.repository.MessageTemplateRepository;
import com.muhjain.school.model.repository.NotificationRepository;
import com.muhjain.school.service.fee.PaymentService;
import com.muhjain.school.service.messaging.OutboxWorker;
import com.muhjain.school.service.notification.AudienceService;
import com.muhjain.school.service.notification.GuardChain;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import com.muhjain.school.service.notification.impl.QuietHoursGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static com.muhjain.school.model.enums.AudienceGroup.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * docs/10-notifications.md section 10: every guard once with a recipient it skips and once with one it lets go, on
 * the small school of {@link NotificationTestBase} (7 Oct 2026, 10 am).
 */
class GuardChainTest extends NotificationTestBase {

	private static final String PUNIA = "+919811100001";

	private static final String CHAUHAN = "+919811100002";

	private static final String SHARMA = "+919811100004";

	@Autowired
	private GuardChain chain;

	@Autowired
	private AudienceService audienceService;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private MessageTemplateRepository templates;

	@Autowired
	private MessageOutboxRepository outbox;

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private OutboxWorker worker;

	// ---------------------------------------------------------------- helpers

	private Notification notification(NotificationKind kind, TemplateApproval approval, AudienceFilter filter,
			AudienceGroup... groups) {
		MessageTemplate template = new MessageTemplate(TemplateKind.of(kind), "Template", "नमस्ते {Parent name}",
				anyUserId());
		template.setApproval(approval, (approval == TemplateApproval.REJECTED) ? "No" : null, anyUserId());
		templates.save(template);
		Notification n = new Notification();
		n.setKind(kind);
		n.setName("Test");
		n.setTemplateId(template.getId());
		n.setBody("नमस्ते {Parent name}");
		n.setAudience(new Audience(List.of(groups), filter));
		n.setStatus(NotificationStatus.SENDING);
		n.setCreatedBy(anyUserId());
		return notifications.save(n);
	}

	private Notification notification(NotificationKind kind, AudienceGroup... groups) {
		return notification(kind, TemplateApproval.APPROVED, null, groups);
	}

	/** The recipients as the dispatcher will see them: the audience now, stopped families included. */
	private List<Recipient> people(Notification n) {
		var resolved = audienceService.resolve(n.getKind(), n.getAudience());
		return java.util.stream.Stream.concat(resolved.recipients().stream(), resolved.stopped().stream()).toList();
	}

	private Recipient person(Notification n, String phone) {
		return people(n).stream().filter(r -> phone.equals(r.phone())).findFirst().orElseThrow();
	}

	private GuardOutcome check(Notification n, Recipient r) {
		return chain.check(r, n, chain.newContext(n));
	}

	private GuardOutcome check(Notification n, String phone) {
		return check(n, person(n, phone));
	}

	private static Recipient guardian(String phone, List<Long> children) {
		return new Recipient(RecipientType.GUARDIAN, 1L, phone, MessageChannel.WHATSAPP, CURRENT_PARENTS, children,
				Map.of());
	}

	private static AudienceFilter filter(List<String> classes, List<Long> routeIds, BigDecimal minAmount,
			Integer remindedDays, Boolean skipPromised) {
		return new AudienceFilter(classes, routeIds, null, null, null, minAmount, remindedDays, skipPromised, null,
				null);
	}

	private void sentBefore(String phone, MessagePurpose purpose, Duration ago) {
		Notification other = notification(NotificationKind.MARKETING, CURRENT_PARENTS);
		jdbc.update("insert into message_outbox (purpose, channel, phone, body, status, notification_id, created_at, "
				+ "next_try_at) values (?, 'WHATSAPP', ?, 'x', 'SENT', ?, ?, ?)", purpose.name(), phone, other.getId(),
				Timestamp.from(clock.instant().minus(ago)), Timestamp.from(clock.instant().minus(ago)));
	}

	private void feePlan(long studentId, String perMonth) {
		jdbc.update("insert into fee_plan (student_id, session_id, from_month, school_fee_per_month, created_by) "
				+ "values (?, ?, date '2026-04-01', ?, ?)", studentId, sessionId, new BigDecimal(perMonth), anyUserId());
	}

	private void pay(long studentId, String amount) {
		paymentService.record(studentId, new PaymentRequest(null, PaymentMode.CASH, null,
				List.of(new PaymentLine(FeeHead.SCHOOL, new BigDecimal(amount)))), anyUserId());
	}

	private void promised(String phone, String until) {
		jdbc.update("insert into inbound_message (phone, text, provider_ref, received_at, promised_until) "
				+ "values (?, 'Friday tak de denge', ?, ?, ?::date)", phone, "wamid." + until + phone,
				Timestamp.from(clock.instant()), until);
	}

	// ---------------------------------------------------------------- the chain itself

	@Test
	void guardsRunInTheirOrder() {
		assertThat(chain.guards()).extracting(g -> g.getClass().getSimpleName())
			.containsExactly("PhoneGuard", "DuplicateGuard", "PersonActiveGuard", "StopListGuard", "WeeklyCapGuard",
					"TemplateApprovedGuard", "WindowGuard", "QuietHoursGuard", "FeeDueGuard", "RouteTodayGuard",
					"ClassFilterGuard", "EnquiryStageGuard", "LastSecondGuard");
		assertThat(chain.guards()).extracting(RecipientGuard::order).isSorted();
	}

	// ---------------------------------------------------------------- 10 to 16

	@Test
	void phoneGuard() {
		Notification n = notification(NotificationKind.NOTICE, CURRENT_PARENTS);
		assertThat(check(n, guardian(null, List.of(aryan))).skipReason()).isEqualTo(SkipReason.NO_PHONE);
		assertThat(check(n, guardian("+911234567890", List.of(aryan))).skipReason()).isEqualTo(SkipReason.NO_PHONE);
		assertThat(check(n, PUNIA).mayGo()).isTrue();
	}

	@Test
	void duplicateGuard() {
		Notification n = notification(NotificationKind.NOTICE, CURRENT_PARENTS);
		GuardContext run = chain.newContext(n);
		assertThat(chain.check(person(n, PUNIA), n, run).mayGo()).isTrue();
		assertThat(chain.check(person(n, PUNIA), n, run).skipReason()).isEqualTo(SkipReason.DUPLICATE);
		// A row written before (a crash in the middle, then the next minute) also counts.
		outbox.save(row(n, MessagePurpose.NOTICE, CHAUHAN, null));
		assertThat(check(n, CHAUHAN).skipReason()).isEqualTo(SkipReason.DUPLICATE);
	}

	@Test
	void personActiveGuard() {
		Notification n = notification(NotificationKind.NOTICE, CURRENT_PARENTS);
		assertThat(check(n, guardian(PUNIA, List.of())).skipReason()).isEqualTo(SkipReason.PERSON_INACTIVE);
		assertThat(check(n, PUNIA).mayGo()).isTrue();

		Notification staffNotice = notification(NotificationKind.STAFF, TEACHERS);
		Recipient sunitaNow = person(staffNotice, "+919811100011");
		jdbc.update("update staff set active = false where id = ?", sunita);
		assertThat(check(staffNotice, sunitaNow).skipReason()).isEqualTo(SkipReason.PERSON_INACTIVE);

		Notification marketing = notification(NotificationKind.MARKETING, ENQUIRY_OPEN);
		Recipient suresh = person(marketing, "+919811100005");
		assertThat(check(marketing, suresh).mayGo()).isTrue();
		jdbc.update("update enquiry set status = 'LOST', status_before_lost = 'VISITED', lost_reason = 'OTHER' "
				+ "where phone = '+919811100005'");
		assertThat(check(marketing, suresh).skipReason()).isEqualTo(SkipReason.PERSON_INACTIVE);
	}

	@Test
	void stopListGuard() {
		Notification marketing = notification(NotificationKind.MARKETING, CURRENT_PARENTS);
		assertThat(check(marketing, CHAUHAN).skipReason()).isEqualTo(SkipReason.STOPPED);
		assertThat(check(marketing, PUNIA).mayGo()).isTrue();
		// A notice still reaches a stopped family.
		Notification notice = notification(NotificationKind.NOTICE, CURRENT_PARENTS);
		assertThat(check(notice, CHAUHAN).mayGo()).isTrue();
	}

	@Test
	void weeklyCapSkipsThirdMarketingInSevenDays() {
		Notification third = notification(NotificationKind.GREETING, CURRENT_PARENTS);
		sentBefore(PUNIA, MessagePurpose.MARKETING, Duration.ofDays(2));
		assertThat(check(third, PUNIA).mayGo()).isTrue();
		sentBefore(PUNIA, MessagePurpose.MARKETING, Duration.ofDays(5));
		assertThat(check(third, PUNIA).skipReason()).isEqualTo(SkipReason.WEEKLY_CAP);
		// 8 days ago does not count, and a notice is no marketing.
		sentBefore(SHARMA, MessagePurpose.MARKETING, Duration.ofDays(8));
		sentBefore(SHARMA, MessagePurpose.NOTICE, Duration.ofDays(1));
		sentBefore(SHARMA, MessagePurpose.MARKETING, Duration.ofDays(1));
		assertThat(check(third, SHARMA).mayGo()).isTrue();
	}

	@Test
	void templateMustBeApproved() {
		Notification waiting = notification(NotificationKind.NOTICE, TemplateApproval.WAITING, null, CURRENT_PARENTS);
		assertThat(check(waiting, PUNIA).skipReason()).isEqualTo(SkipReason.TEMPLATE_NOT_APPROVED);
		Notification rejected = notification(NotificationKind.MARKETING, TemplateApproval.REJECTED, null,
				CURRENT_PARENTS);
		assertThat(check(rejected, PUNIA).skipReason()).isEqualTo(SkipReason.TEMPLATE_NOT_APPROVED);
		assertThat(check(notification(NotificationKind.NOTICE, CURRENT_PARENTS), PUNIA).mayGo()).isTrue();
	}

	@Test
	void freeTextAllowedInside24hWindow() {
		AudienceFilter puniaOnly = new AudienceFilter(null, null, null, null, null, null, null, null, List.of(punia),
				null);
		Notification freeText = notification(NotificationKind.ONE_FAMILY, TemplateApproval.WAITING, puniaOnly,
				AudienceGroup.ONE_FAMILY);
		assertThat(check(freeText, PUNIA).skipReason()).isEqualTo(SkipReason.OUTSIDE_WINDOW);

		jdbc.update("insert into inbound_message (phone, text, provider_ref, received_at) values (?, 'Bus late?', "
				+ "'wamid.old', ?)", PUNIA, Timestamp.from(clock.instant().minus(Duration.ofHours(25))));
		assertThat(check(freeText, PUNIA).skipReason()).isEqualTo(SkipReason.OUTSIDE_WINDOW);
		jdbc.update("insert into inbound_message (phone, text, provider_ref, received_at) values (?, 'Bus late?', "
				+ "'wamid.new', ?)", PUNIA, Timestamp.from(clock.instant().minus(Duration.ofHours(2))));
		assertThat(check(freeText, PUNIA).mayGo()).isTrue();
	}

	// ---------------------------------------------------------------- 20

	@Test
	void quietHoursHoldTheMessageButNeverSkipIt() {
		Notification greeting = notification(NotificationKind.GREETING, CURRENT_PARENTS);
		assertThat(check(greeting, PUNIA).holdUntil()).isNull();   // 10 am

		clock.setInstant(Instant.parse("2026-10-07T16:45:00Z"));  // 22:15 school time
		GuardOutcome night = check(greeting, PUNIA);
		assertThat(night.mayGo()).isTrue();
		assertThat(night.holdUntil()).isEqualTo(Instant.parse("2026-10-08T02:30:00Z"));  // 08:00 next morning

		Notification urgent = notification(NotificationKind.URGENT, CURRENT_PARENTS);
		assertThat(check(urgent, PUNIA).holdUntil()).isNull();
	}

	@Test
	void quietEndWorksOverMidnightAndInsideOneDay() {
		ZoneId zone = ZoneId.of("Asia/Kolkata");
		LocalTime nine = LocalTime.of(21, 0);
		LocalTime eight = LocalTime.of(8, 0);
		ZonedDateTime early = ZonedDateTime.of(2026, 10, 8, 6, 30, 0, 0, zone);
		assertThat(QuietHoursGuard.quietEnd(early, nine, eight)).contains(early.withHour(8).withMinute(0));
		assertThat(QuietHoursGuard.quietEnd(early.withHour(8), nine, eight)).isEmpty();
		assertThat(QuietHoursGuard.quietEnd(early.withHour(21), nine, eight)).contains(early.plusDays(1).withHour(8)
			.withMinute(0));
		LocalTime lunch = LocalTime.of(13, 0);
		assertThat(QuietHoursGuard.quietEnd(early.withHour(13).withMinute(20), lunch, LocalTime.of(14, 0)))
			.contains(early.withHour(14).withMinute(0));
		assertThat(QuietHoursGuard.quietEnd(early, nine, nine)).isEmpty();
	}

	// ---------------------------------------------------------------- 50: the fee reminder

	@Test
	void feeReminderFillsTheLiveNumbers() {
		Notification reminder = notification(NotificationKind.FEE_REMINDER, FAMILIES_WITH_DUES);
		GuardOutcome sharmaNow = check(reminder, SHARMA);
		assertThat(sharmaNow.mayGo()).isTrue();
		// Aug and Sep are late (grace day 10), Oct not yet: 2 × ₹100 late fee (the session's PER_MONTH rule).
		assertThat(sharmaNow.values()).containsEntry("Amount due", "₹9,600")
			.containsEntry("Months", "अगस्त, सितंबर और अक्टूबर")
			.containsEntry("Months late", "2")
			.containsEntry("Late fee", "₹200");
	}

	@Test
	void feeReminderAmountComesFromLiveDuesNotDraft() {
		Notification reminder = notification(NotificationKind.FEE_REMINDER, FAMILIES_WITH_DUES);
		Recipient atDraftTime = person(reminder, SHARMA);
		assertThat(atDraftTime.values()).containsEntry("Amount due", "₹9,600");
		// August is paid off another way before the send (the due is removed): the guard reads ₹6,400 now.
		jdbc.update("delete from fee_due where student_id = ? and month = date '2026-08-01'", dev);
		assertThat(check(reminder, atDraftTime).values()).containsEntry("Amount due", "₹6,400")
			.containsEntry("Months late", "1");
	}

	@Test
	void feeReminderSkipsFamilyThatPaidAfterScheduling() {
		// Scheduled on the 6th for today; the Sharma family pays everything this morning through the office.
		Notification reminder = notification(NotificationKind.FEE_REMINDER, FAMILIES_WITH_DUES);
		Recipient sharmaFamily = person(reminder, SHARMA);
		feePlan(dev, "3200");
		pay(dev, "9600");
		assertThat(check(reminder, sharmaFamily).skipReason()).isEqualTo(SkipReason.ALREADY_PAID);
	}

	@Test
	void aReceiptTodayCountsAsPaidEvenIfSomethingIsLeft() {
		Notification reminder = notification(NotificationKind.FEE_REMINDER, FAMILIES_WITH_DUES);
		feePlan(dev, "3200");
		pay(dev, "3200");   // August only; September and October are still due
		assertThat(check(reminder, SHARMA).skipReason()).isEqualTo(SkipReason.ALREADY_PAID);
	}

	@Test
	void creditThatCoversTheDuesSkips() {
		Notification reminder = notification(NotificationKind.FEE_REMINDER, FAMILIES_WITH_DUES);
		jdbc.update("insert into family_credit_entry (guardian_id, amount, reason, created_by) "
				+ "values (?, 5000, 'PAID_AHEAD_KEPT', ?)", sharma, anyUserId());
		assertThat(check(reminder, SHARMA).mayGo()).isTrue();   // 5000 < 9600
		jdbc.update("insert into family_credit_entry (guardian_id, amount, reason, created_by) "
				+ "values (?, 5000, 'PAID_AHEAD_KEPT', ?)", sharma, anyUserId());
		assertThat(check(reminder, SHARMA).skipReason()).isEqualTo(SkipReason.CREDIT_COVERS);
	}

	@Test
	void belowTheFilterNow() {
		Notification reminder = notification(NotificationKind.FEE_REMINDER, TemplateApproval.APPROVED,
				filter(null, null, new BigDecimal("9000"), null, null), FAMILIES_WITH_DUES);
		Recipient sharmaFamily = person(reminder, SHARMA);
		assertThat(check(reminder, sharmaFamily).mayGo()).isTrue();
		jdbc.update("delete from fee_due where student_id = ? and month = date '2026-08-01'", dev);
		assertThat(check(reminder, sharmaFamily).skipReason()).isEqualTo(SkipReason.BELOW_FILTER);
	}

	@Test
	void feeReminderSkipsPromisedFamilyUntilPromiseDate() {
		Notification reminder = notification(NotificationKind.FEE_REMINDER, FAMILIES_WITH_DUES);
		promised(SHARMA, "2026-10-09");   // "will pay by Friday"
		assertThat(check(reminder, SHARMA).skipReason()).isEqualTo(SkipReason.PROMISED);
		// The office can switch the rule off for one reminder.
		Notification ignorePromises = notification(NotificationKind.FEE_REMINDER, TemplateApproval.APPROVED,
				filter(null, null, null, null, false), FAMILIES_WITH_DUES);
		assertThat(check(ignorePromises, SHARMA).mayGo()).isTrue();
		// After Friday the family is back in.
		clock.setInstant(Instant.parse("2026-10-10T04:30:00Z"));
		assertThat(check(reminder, SHARMA).mayGo()).isTrue();
	}

	@Test
	void remindedRecentlySkips() {
		Notification reminder = notification(NotificationKind.FEE_REMINDER, FAMILIES_WITH_DUES);
		sentBefore(SHARMA, MessagePurpose.FEE_REMINDER, Duration.ofDays(3));
		assertThat(check(reminder, SHARMA).skipReason()).isEqualTo(SkipReason.REMINDED_RECENTLY);
		Notification twoDays = notification(NotificationKind.FEE_REMINDER, TemplateApproval.APPROVED,
				filter(null, null, null, 2, null), FAMILIES_WITH_DUES);
		assertThat(check(twoDays, SHARMA).mayGo()).isTrue();
	}

	// ---------------------------------------------------------------- 51 to 53

	@Test
	void urgentSkipsChildNotOnRouteToday() {
		Notification busLate = notification(NotificationKind.URGENT, TemplateApproval.APPROVED,
				filter(null, List.of(route4), null, null, null), ROUTE);
		Recipient puniaFamily = person(busLate, PUNIA);
		assertThat(check(busLate, puniaFamily).mayGo()).isTrue();
		// Aryan's bus ended yesterday (he moved): not on Route 4 today any more.
		jdbc.update("update transport_enrolment set to_date = date '2026-10-06' where student_id = ?", aryan);
		assertThat(check(busLate, puniaFamily).skipReason()).isEqualTo(SkipReason.NOT_ON_ROUTE_TODAY);
	}

	@Test
	void noBusOnAHoliday() {
		Notification busLate = notification(NotificationKind.URGENT, TemplateApproval.APPROVED,
				filter(null, List.of(route4), null, null, null), ROUTE);
		Recipient puniaFamily = person(busLate, PUNIA);
		jdbc.update("insert into holiday (holiday_date, name, created_by) values (date '2026-10-07', 'Rain', ?)",
				anyUserId());
		assertThat(check(busLate, puniaFamily).skipReason()).isEqualTo(SkipReason.NOT_ON_ROUTE_TODAY);
		// The same for the crew of the route.
		Notification crew = notification(NotificationKind.STAFF, TemplateApproval.APPROVED,
				filter(null, List.of(route4), null, null, null), ROUTE_CREW);
		assertThat(check(crew, "+919811100010").skipReason()).isEqualTo(SkipReason.NOT_ON_ROUTE_TODAY);
	}

	@Test
	void classFilterGuard() {
		Notification class10 = notification(NotificationKind.NOTICE, TemplateApproval.APPROVED,
				filter(List.of("10"), null, null, null, null), CURRENT_PARENTS);
		Recipient chauhanFamily = person(class10, CHAUHAN);
		assertThat(check(class10, chauhanFamily).mayGo()).isTrue();
		jdbc.update("update student set class_name = '11' where name = 'Kavya Chauhan'");
		assertThat(check(class10, chauhanFamily).skipReason()).isEqualTo(SkipReason.FILTER_NO_LONGER_MATCHES);
	}

	@Test
	void enquiryStageGuard() {
		Notification marketing = notification(NotificationKind.MARKETING, ENQUIRY_OPEN);
		Recipient suresh = person(marketing, "+919811100005");
		assertThat(check(marketing, suresh).mayGo()).isTrue();
		jdbc.update("update enquiry set status = 'ADMITTED', admitted_student_id = ? where phone = '+919811100005'",
				siya);
		assertThat(check(marketing, suresh).skipReason()).isEqualTo(SkipReason.NOW_ADMITTED);
	}

	// ---------------------------------------------------------------- 90: in the worker

	private MessageOutbox row(Notification n, MessagePurpose purpose, String phone, Long guardianId) {
		MessageOutbox row = new MessageOutbox(purpose, MessageChannel.WHATSAPP, phone, "x", clock.instant());
		row.setNotificationId(n.getId());
		row.setGuardianId(guardianId);
		return row;
	}

	@Test
	void lastSecondGuardSkipsPaymentBetweenExpandAndSend() {
		Notification reminder = notification(NotificationKind.FEE_REMINDER, FAMILIES_WITH_DUES);
		// 10:00 expand: the Sharma family still owes, so a row is written.
		assertThat(check(reminder, SHARMA).mayGo()).isTrue();
		long id = outbox.save(row(reminder, MessagePurpose.FEE_REMINDER, SHARMA, sharma)).getId();
		long other = outbox.save(row(reminder, MessagePurpose.FEE_REMINDER, PUNIA, punia)).getId();
		// 10:01 the family pays everything at the office, before the worker reaches the row.
		feePlan(dev, "3200");
		pay(dev, "9600");

		worker.runOnce();

		MessageOutbox skipped = outbox.findById(id).orElseThrow();
		assertThat(skipped.getStatus()).isEqualTo(MessageStatus.SKIPPED);
		assertThat(skipped.getSkipReason()).isEqualTo(SkipReason.ALREADY_PAID);
		assertThat(skipped.getSentAt()).isNull();
		// The Punia family did not pay: their row went (test mode).
		assertThat(outbox.findById(other).orElseThrow().getStatus()).isEqualTo(MessageStatus.TEST_ONLY);
	}

	@Test
	void urgentOlderThanTwoHoursIsNotSent() {
		Notification urgent = notification(NotificationKind.URGENT, CURRENT_PARENTS);
		long old = outbox.save(row(urgent, MessagePurpose.URGENT, PUNIA, punia)).getId();
		long fresh = outbox.save(row(urgent, MessagePurpose.URGENT, CHAUHAN, chauhan)).getId();
		jdbc.update("update message_outbox set created_at = ? where id = ?",
				Timestamp.from(clock.instant().minus(Duration.ofHours(3))), old);
		jdbc.update("update message_outbox set created_at = ? where id = ?",
				Timestamp.from(clock.instant().minus(Duration.ofMinutes(30))), fresh);

		worker.runOnce();

		assertThat(outbox.findById(old).orElseThrow().getSkipReason()).isEqualTo(SkipReason.TOO_OLD);
		assertThat(outbox.findById(fresh).orElseThrow().getStatus()).isEqualTo(MessageStatus.TEST_ONLY);
	}

	@Test
	void aHeldRowWaitsForItsTime() {
		Notification greeting = notification(NotificationKind.GREETING, CURRENT_PARENTS);
		MessageOutbox held = row(greeting, MessagePurpose.MARKETING, PUNIA, punia);
		held.setNextTryAt(clock.instant().plus(Duration.ofHours(10)));
		long id = outbox.save(held).getId();

		assertThat(worker.runOnce()).isZero();
		assertThat(outbox.findById(id).orElseThrow().getStatus()).isEqualTo(MessageStatus.QUEUED);
		clock.setInstant(clock.instant().plus(Duration.ofHours(10)));
		assertThat(worker.runOnce()).isEqualTo(1);
	}

}

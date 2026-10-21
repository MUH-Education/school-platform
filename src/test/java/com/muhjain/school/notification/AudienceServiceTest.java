package com.muhjain.school.notification;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.dto.response.AudienceCounts;
import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.ResolvedAudience;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.FeeStatus;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.RecipientType;
import com.muhjain.school.service.notification.AudienceService;
import com.muhjain.school.service.notification.impl.EnquiryResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.muhjain.school.model.enums.AudienceGroup.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * docs/10-notifications.md section 3, with a small school on 7 Oct 2026:
 * <ul>
 * <li>Punia family (+919811100001): Aryan (3, Route 4) and Siya (11). Dues: Aryan Sep + Oct, Siya Oct.</li>
 * <li>Chauhan family (+919811100002): Kavya (10), Jakhal. On the stop list. No dues.</li>
 * <li>Malik family (+919811100003): Riya (10, Route 4) — Riya has LEFT the school.</li>
 * <li>Sharma family (+919811100004): Dev (10). Dues: Aug, Sep, Oct.</li>
 * <li>Mohan (5): no phone saved at all.</li>
 * <li>Staff: Balwan (attendant of Route 4's van, and Aryan's father: +919811100001), Jagdish (driver of the van),
 * Sunita (teacher), Karan (teacher, turned off).</li>
 * <li>Enquiries: Suresh (open, +919811100005), Meena (lost, +919811100002 = the Chauhan phone).</li>
 * </ul>
 */
class AudienceServiceTest extends AbstractIntegrationTest {

	@Autowired
	private AudienceService audienceService;

	private long punia;

	private long chauhan;

	private long sharma;

	private long route4;

	private long aryan;

	private long siya;

	private long balwan;

	private long jagdish;

	private long sunita;

	private long sessionId;

	@BeforeEach
	void smallSchool() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		sessionId = jdbc.queryForObject("select id from academic_session where name = '2026-27'", Long.class);
		long van = addVehicle("Van 4");
		route4 = jdbc.queryForObject("insert into route (name, vehicle_id) values ('Route 4', ?) returning id", Long.class,
				van);
		long stop = jdbc.queryForObject("insert into route_stop (route_id, name, seq_no, morning_time, evening_time) "
				+ "values (?, 'Jakhal', 1, time '07:40', time '15:00') returning id", Long.class, route4);

		punia = guardian("Rajesh Punia", "+919811100001");
		chauhan = guardian("Vikram Chauhan", "+919811100002");
		long malik = guardian("Suresh Malik", "+919811100003");
		sharma = guardian("Anil Sharma", "+919811100004");

		aryan = child("A-2026-101", "Aryan Punia", "3", "Jakhal", punia);
		siya = child("A-2026-102", "Siya Punia", "11", "Jakhal", punia);
		child("A-2026-103", "Kavya Chauhan", "10", "Jakhal", chauhan);
		long riya = child("A-2026-104", "Riya Malik", "10", "Tohana", malik);
		long dev = child("A-2026-105", "Dev Sharma", "10", "Tohana", sharma);
		child("A-2026-106", "Mohan", "5", "Tohana", null);
		jdbc.update("update student set status = 'LEFT', left_on = date '2026-09-30', left_reason = 'OTHER' where id = ?",
				riya);
		bus(aryan, stop);
		bus(riya, stop);

		due(aryan, "2026-09-01", "2500");
		due(aryan, "2026-10-01", "2500");
		due(siya, "2026-10-01", "3500");
		due(dev, "2026-08-01", "3200");
		due(dev, "2026-09-01", "3200");
		due(dev, "2026-10-01", "3200");

		balwan = staff("Balwan", "ATTENDANT", "+919811100001");
		jagdish = staff("Jagdish", "DRIVER", "+919811100010");
		sunita = staff("Sunita", "TEACHER", "+919811100011");
		long karan = staff("Karan", "TEACHER", "+919811100012");
		jdbc.update("update staff set active = false where id = ?", karan);
		addAssignment(van, jagdish, "DRIVER", "2026-04-01", null, false);
		addAssignment(van, balwan, "ATTENDANT", "2026-04-01", null, false);

		enquiry("Suresh", "+919811100005", "1", "VISITED");
		enquiry("Meena", "+919811100002", "2", "LOST");

		jdbc.update("insert into message_stop (guardian_id, reason) values (?, 'WROTE_STOP')", chauhan);
	}

	private long guardian(String name, String phone) {
		return jdbc.queryForObject("insert into guardian (name, phone) values (?, ?) returning id", Long.class, name,
				phone);
	}

	private long child(String admissionNo, String name, String className, String village, Long guardianId) {
		long id = jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
				+ "father_occupation, joined_on, created_by) values (?, ?, date '2016-05-14', 'M', ?, ?, 'OTHER', "
				+ "date '2026-04-01', ?) returning id", Long.class, admissionNo, name, className, village, anyUserId());
		if (guardianId != null) {
			jdbc.update("insert into student_guardian (student_id, guardian_id, relation, is_primary) "
					+ "values (?, ?, 'FATHER', true)", id, guardianId);
		}
		return id;
	}

	private void bus(long studentId, long stopId) {
		jdbc.update("insert into transport_enrolment (student_id, uses_bus, route_id, stop_id, from_date, "
				+ "bus_fee_per_month, reason, created_by) values (?, true, ?, ?, date '2026-04-01', 800, 'ADMISSION', ?)",
				studentId, route4, stopId, anyUserId());
	}

	private void due(long studentId, String month, String amount) {
		jdbc.update("insert into fee_due (student_id, session_id, month, school_amount) values (?, ?, ?::date, ?)",
				studentId, sessionId, month, new BigDecimal(amount));
	}

	private long staff(String name, String type, String phone) {
		long id = addStaff(name, type);
		jdbc.update("update staff set phone = ? where id = ?", phone, id);
		return id;
	}

	private void enquiry(String parent, String phone, String cls, String status) {
		jdbc.update("insert into enquiry (session_id, parent_name, phone, village, class_sought, source, status, "
				+ "status_before_lost, lost_reason, created_by) values (?, ?, ?, 'Tohana', ?, 'WALK_IN', ?, ?, ?, ?)",
				sessionId, parent, phone, cls, status, "LOST".equals(status) ? "CONTACTED" : null,
				"LOST".equals(status) ? "OTHER" : null, anyUserId());
	}

	private ResolvedAudience resolve(NotificationKind kind, AudienceFilter filter, AudienceGroup... groups) {
		return audienceService.resolve(kind, new Audience(List.of(groups), filter));
	}

	private static AudienceFilter filter(List<String> classes, List<Long> routeIds, FeeStatus feeStatus,
			Integer minMonthsLate, BigDecimal minAmount, List<Long> familyIds, List<Long> staffIds) {
		return new AudienceFilter(classes, routeIds, null, feeStatus, minMonthsLate, minAmount, null, null, familyIds,
				staffIds);
	}

	private static Recipient byPhone(ResolvedAudience result, String phone) {
		return result.recipients().stream().filter(r -> phone.equals(r.phone())).findFirst().orElseThrow();
	}

	@Test
	void oneRowPerFamilyNotPerChild() {
		ResolvedAudience result = resolve(NotificationKind.NOTICE, null, CURRENT_PARENTS);

		Recipient puniaFamily = byPhone(result, "+919811100001");
		assertThat(puniaFamily.type()).isEqualTo(RecipientType.GUARDIAN);
		assertThat(puniaFamily.personId()).isEqualTo(punia);
		assertThat(puniaFamily.studentIds()).containsExactlyInAnyOrder(aryan, siya);
		assertThat(puniaFamily.values()).containsEntry("Child name", "Aryan और Siya")
			.containsEntry("Class", "3 और 11")
			.containsEntry("Parent name", "Rajesh Punia");
		// Punia, Chauhan, Sharma. Malik's only child has left. Mohan has no phone.
		assertThat(result.recipients()).extracting(Recipient::phone)
			.containsExactlyInAnyOrder("+919811100001", "+919811100002", "+919811100004");
		assertThat(result.counts()).isEqualTo(new AudienceCounts(4, 0, 0, 1, 3, 3, 0));
	}

	@Test
	void familyInTwoGroupsCountedOnce() {
		// Balwan (attendant) is also Aryan's father: picked twice, sent once, as the parent (first group ticked).
		ResolvedAudience result = resolve(NotificationKind.MARKETING, null, CURRENT_PARENTS, STAFF_ALL);
		assertThat(result.recipients()).filteredOn(r -> "+919811100001".equals(r.phone())).hasSize(1);
		assertThat(byPhone(result, "+919811100001").type()).isEqualTo(RecipientType.GUARDIAN);
		assertThat(result.counts().duplicates()).isEqualTo(1);
		// Karan is turned off; Balwan counted as a parent; Jagdish and Sunita as staff.
		assertThat(result.recipients()).filteredOn(r -> r.type() == RecipientType.STAFF)
			.extracting(Recipient::personId)
			.containsExactlyInAnyOrder(jagdish, sunita);
	}

	@Test
	void stopListBlocksMarketingAndGreetingOnly() {
		for (NotificationKind kind : List.of(NotificationKind.MARKETING, NotificationKind.GREETING)) {
			ResolvedAudience result = resolve(kind, null, CURRENT_PARENTS, ENQUIRY_LOST);
			// Chauhan said stop: gone as a parent, and as the lost enquiry Meena (the same phone).
			assertThat(result.recipients()).extracting(Recipient::phone).doesNotContain("+919811100002");
			assertThat(result.stopped()).extracting(Recipient::phone).containsExactly("+919811100002");
			assertThat(result.counts().stopped()).as("%s", kind).isEqualTo(1);
			assertThat(result.counts().duplicates()).isEqualTo(1);
		}
	}

	@Test
	void noticeAndUrgentIgnoreStopList() {
		for (NotificationKind kind : List.of(NotificationKind.NOTICE, NotificationKind.URGENT)) {
			ResolvedAudience result = resolve(kind, null, CURRENT_PARENTS);
			assertThat(result.recipients()).extracting(Recipient::phone).contains("+919811100002");
			assertThat(result.stopped()).isEmpty();
		}
	}

	@Test
	void urgentCountsEveryoneOnSmsToo() {
		AudienceCounts counts = resolve(NotificationKind.URGENT, null, CURRENT_PARENTS).counts();
		assertThat(counts.total()).isEqualTo(3);
		assertThat(counts.onWhatsApp()).isEqualTo(3);
		assertThat(counts.onSms()).isEqualTo(3);
	}

	@Test
	void classAndRouteFilters() {
		AudienceFilter class10 = filter(List.of("10"), null, null, null, null, null, null);
		assertThat(resolve(NotificationKind.NOTICE, class10, CURRENT_PARENTS).recipients()).extracting(Recipient::phone)
			.containsExactlyInAnyOrder("+919811100002", "+919811100004");

		// On Route 4 today: only Aryan (Riya has left). The family message names the route.
		ResolvedAudience onRoute = resolve(NotificationKind.URGENT,
				filter(null, List.of(route4), null, null, null, null, null), ROUTE);
		assertThat(onRoute.recipients()).hasSize(1);
		assertThat(onRoute.recipients().getFirst().values()).containsEntry("Child name", "Aryan")
			.containsEntry("Route", "Route 4");
		// No route picked → nobody, never "every route".
		assertThat(resolve(NotificationKind.URGENT, null, ROUTE).recipients()).isEmpty();
	}

	@Test
	void familiesWithDuesGetTheLiveNumbers() {
		ResolvedAudience result = resolve(NotificationKind.FEE_REMINDER, null, FAMILIES_WITH_DUES);
		assertThat(result.recipients()).extracting(Recipient::phone)
			.containsExactlyInAnyOrder("+919811100001", "+919811100004");
		// Punia: Aryan Sep 2500 + Oct 2500, Siya Oct 3500. September is late (grace day 10), October not yet.
		assertThat(byPhone(result, "+919811100001").values()).containsEntry("Amount due", "₹8,500")
			.containsEntry("Months", "सितंबर और अक्टूबर")
			.containsEntry("Months late", "1")
			.containsEntry("Child name", "Aryan और Siya");
		assertThat(byPhone(result, "+919811100004").values()).containsEntry("Amount due", "₹9,600")
			.containsEntry("Months", "अगस्त, सितंबर और अक्टूबर")
			.containsEntry("Months late", "2");

		// Paid since: the next resolve does not have them (the list is made fresh every time).
		jdbc.update("delete from fee_due where student_id = ?", aryan);
		jdbc.update("delete from fee_due where student_id = ?", siya);
		assertThat(resolve(NotificationKind.FEE_REMINDER, null, FAMILIES_WITH_DUES).recipients())
			.extracting(Recipient::phone)
			.containsExactly("+919811100004");
	}

	@Test
	void feeFiltersAreOnTheFamilyTotal() {
		assertThat(resolve(NotificationKind.FEE_REMINDER, filter(null, null, null, 2, null, null, null),
				FAMILIES_WITH_DUES).recipients()).extracting(Recipient::phone).containsExactly("+919811100004");
		assertThat(resolve(NotificationKind.FEE_REMINDER, filter(null, null, null, null, new BigDecimal("9000"), null,
				null), FAMILIES_WITH_DUES).recipients()).extracting(Recipient::phone).containsExactly("+919811100004");
		// Fee status filter on CURRENT_PARENTS: Sharma's oldest unpaid month is 67 days late → DEFAULTED.
		assertThat(resolve(NotificationKind.NOTICE, filter(null, null, FeeStatus.DEFAULTED, null, null, null, null),
				CURRENT_PARENTS).recipients()).extracting(Recipient::phone).containsExactly("+919811100004");
	}

	@Test
	void staffGroupsByTypeAndOnlyTurnedOn() {
		assertThat(resolve(NotificationKind.STAFF, null, TEACHERS).recipients()).extracting(Recipient::personId)
			.containsExactly(sunita);
		assertThat(resolve(NotificationKind.STAFF, null, DRIVERS).recipients()).extracting(Recipient::personId)
			.containsExactly(jagdish);
		Recipient staffMember = resolve(NotificationKind.STAFF, null, ATTENDANTS).recipients().getFirst();
		assertThat(staffMember.values()).containsEntry("Name", "Balwan").containsEntry("Staff name", "Balwan");
		assertThat(resolve(NotificationKind.STAFF, null, STAFF_ALL).recipients()).hasSize(3);
	}

	@Test
	void routeCrewIsWhoWorksOnTheVanToday() {
		ResolvedAudience crew = resolve(NotificationKind.STAFF, filter(null, List.of(route4), null, null, null, null,
				null), ROUTE_CREW);
		assertThat(crew.recipients()).extracting(Recipient::personId).containsExactlyInAnyOrder(jagdish, balwan);
		assertThat(crew.recipients().getFirst().values()).containsEntry("Route", "Route 4");
	}

	@Test
	void enquiriesOpenOrLost() {
		ResolvedAudience open = resolve(NotificationKind.MARKETING, null, ENQUIRY_OPEN);
		assertThat(open.recipients()).extracting(Recipient::phone).containsExactly("+919811100005");
		assertThat(open.recipients().getFirst().type()).isEqualTo(RecipientType.ENQUIRY);
		assertThat(open.recipients().getFirst().values()).containsEntry("Parent name", "Suresh")
			.containsEntry("Class", "1");
		// Meena (lost) has the Chauhan phone, which is stopped.
		assertThat(resolve(NotificationKind.MARKETING, null, ENQUIRY_LOST).recipients()).isEmpty();
	}

	@Test
	void oneFamilyAndOneStaffByTheirIds() {
		ResolvedAudience family = resolve(NotificationKind.ONE_FAMILY,
				filter(null, null, null, null, null, List.of(punia, -1L), null), AudienceGroup.ONE_FAMILY);
		assertThat(family.recipients()).hasSize(1);
		assertThat(family.recipients().getFirst().values()).containsEntry("Child name", "Aryan और Siya");

		ResolvedAudience one = resolve(NotificationKind.STAFF, filter(null, null, null, null, null, null,
				List.of(sunita)), ONE_STAFF);
		assertThat(one.recipients()).extracting(Recipient::personId).containsExactly(sunita);
	}

	@Test
	void valuesNeverHoldNulls() {
		ResolvedAudience result = resolve(NotificationKind.MARKETING, null, CURRENT_PARENTS, STAFF_ALL, ENQUIRY_OPEN);
		for (Recipient r : result.recipients()) {
			assertThat(r.values()).as("%s", r.phone()).doesNotContainValue(null).containsKey("Name");
		}
		assertThat(Map.copyOf(byPhone(result, "+919811100005").values())).containsEntry("Child name", "");
	}

	@Test
	void theAppDoesNotStartWithAGroupWithoutResolver() {
		assertThatThrownBy(() -> new AudienceService(List.of(new EnquiryResolver(null)), null, null, null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageStartingWith("No RecipientResolver for ");
		assertThatThrownBy(() -> new AudienceService(List.of(new EnquiryResolver(null), new EnquiryResolver(null)),
				null, null, null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageStartingWith("Two resolvers for ");
	}

}

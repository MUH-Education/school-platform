package com.muhjain.school.notification;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.muhjain.school.model.enums.AudienceGroup.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** docs/10-notifications.md section 3, on the small school of {@link NotificationTestBase}. */
class AudienceServiceTest extends NotificationTestBase {

	@Autowired
	private AudienceService audienceService;

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
		// A route filter on current parents: children with no bus (route null) are simply not on it.
		assertThat(resolve(NotificationKind.NOTICE, filter(null, List.of(route4), null, null, null, null, null),
				CURRENT_PARENTS).recipients()).extracting(Recipient::phone).containsExactly("+919811100001");
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
		assertThatThrownBy(() -> new AudienceService(List.of(new EnquiryResolver(null)), null, null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageStartingWith("No RecipientResolver for ");
		assertThatThrownBy(() -> new AudienceService(List.of(new EnquiryResolver(null), new EnquiryResolver(null)),
				null, null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageStartingWith("Two resolvers for ");
	}

}

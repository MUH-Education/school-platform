package com.muhjain.school.notification;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.service.notification.KindPolicies;
import com.muhjain.school.service.notification.KindPolicy;
import com.muhjain.school.service.notification.impl.MarketingPolicy;
import com.muhjain.school.service.notification.impl.NoticePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.muhjain.school.model.enums.AudienceGroup.*;
import static com.muhjain.school.model.enums.NotificationKind.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The table of docs/10-notifications.md section 2, one row per kind, read from the real Spring beans. */
class KindPoliciesTest extends AbstractIntegrationTest {

	@Autowired
	private KindPolicies policies;

	private KindPolicy policy(NotificationKind kind) {
		return policies.forKind(kind);
	}

	@Test
	void everyKindHasOnePolicy() {
		for (NotificationKind kind : NotificationKind.values()) {
			assertThat(policy(kind)).as("%s", kind).isNotNull();
			assertThat(policy(kind).kind()).isEqualTo(kind);
		}
	}

	@Test
	void stopListBlocksMarketingAndGreetingOnly() {
		assertThat(Arrays.stream(NotificationKind.values()).filter(k -> policy(k).stopListApplies()))
			.containsExactlyInAnyOrder(MARKETING, GREETING);
	}

	@Test
	void noticeAndUrgentIgnoreStopList() {
		assertThat(policy(NOTICE).stopListApplies()).isFalse();
		assertThat(policy(URGENT).stopListApplies()).isFalse();
		assertThat(policy(FEE_REMINDER).stopListApplies()).isFalse();
	}

	@Test
	void urgentGoesOnBothChannelsAndIgnoresQuietHours() {
		for (NotificationKind kind : NotificationKind.values()) {
			boolean urgent = kind == URGENT;
			assertThat(policy(kind).ignoresQuietHours()).as("%s quiet hours", kind).isEqualTo(urgent);
			assertThat(policy(kind).alsoSmsWhenOnWhatsApp()).as("%s both channels", kind).isEqualTo(urgent);
		}
	}

	@Test
	void freeTextOnlyForOneFamilyAndStaff() {
		assertThat(Arrays.stream(NotificationKind.values()).filter(k -> policy(k).freeTextInsideWindow()))
			.containsExactlyInAnyOrder(NotificationKind.ONE_FAMILY, NotificationKind.STAFF);
	}

	@Test
	void audiencesPerKind() {
		Set<AudienceGroup> staff = EnumSet.of(STAFF_ALL, DRIVERS, ATTENDANTS, TEACHERS);
		assertThat(policy(NOTICE).allowedAudiences()).containsExactlyInAnyOrder(CURRENT_PARENTS, AudienceGroup.ONE_FAMILY);
		assertThat(policy(URGENT).allowedAudiences()).containsAll(staff)
			.contains(CURRENT_PARENTS, ROUTE, ROUTE_CREW, BUS_CHILDREN_WITHOUT_TAP_TODAY)
			.hasSize(8);
		assertThat(policy(MARKETING).allowedAudiences()).containsAll(staff)
			.contains(CURRENT_PARENTS, ENQUIRY_OPEN, ENQUIRY_LOST)
			.hasSize(7);
		// A greeting reaches the same people as marketing, plus the birthday rule's children (Phase 12).
		assertThat(policy(GREETING).allowedAudiences()).containsAll(policy(MARKETING).allowedAudiences())
			.contains(BIRTHDAY_TODAY)
			.hasSize(8);
		assertThat(policy(FEE_REMINDER).allowedAudiences()).containsExactly(FAMILIES_WITH_DUES);
		assertThat(policy(NotificationKind.ONE_FAMILY).allowedAudiences()).containsExactly(AudienceGroup.ONE_FAMILY);
		assertThat(policy(NotificationKind.STAFF).allowedAudiences()).containsAll(staff)
			.contains(ROUTE_CREW, ONE_STAFF)
			.hasSize(6);
		// Enquiries never get a notice or a fee reminder.
		for (NotificationKind kind : List.of(NOTICE, FEE_REMINDER, URGENT, NotificationKind.STAFF)) {
			assertThat(policy(kind).allowedAudiences()).doesNotContain(ENQUIRY_OPEN, ENQUIRY_LOST);
		}
	}

	@Test
	void outboxPurposePerKind() {
		assertThat(policy(NOTICE).outboxPurpose()).isEqualTo(MessagePurpose.NOTICE);
		assertThat(policy(URGENT).outboxPurpose()).isEqualTo(MessagePurpose.URGENT);
		assertThat(policy(MARKETING).outboxPurpose()).isEqualTo(MessagePurpose.MARKETING);
		assertThat(policy(GREETING).outboxPurpose()).isEqualTo(MessagePurpose.MARKETING);
		assertThat(policy(FEE_REMINDER).outboxPurpose()).isEqualTo(MessagePurpose.FEE_REMINDER);
		assertThat(policy(NotificationKind.ONE_FAMILY).outboxPurpose()).isEqualTo(MessagePurpose.ONE_FAMILY);
		assertThat(policy(NotificationKind.STAFF).outboxPurpose()).isEqualTo(MessagePurpose.STAFF);
	}

	@Test
	void onlyTheOwnerMaySendAnyKindForNow() {
		for (NotificationKind kind : NotificationKind.values()) {
			policies.checkMaySend(kind, Role.OWNER.permissions());
			for (Role role : List.of(Role.OFFICE_ADMIN, Role.TRANSPORT_INCHARGE, Role.ADMISSIONS_DESK, Role.ATTENDANT)) {
				assertThatThrownBy(() -> policies.checkMaySend(kind, role.permissions())).as("%s %s", role, kind)
					.isInstanceOfSatisfying(ApiException.class, ex -> {
						assertThat(ex.getStatus().value()).isEqualTo(403);
						assertThat(ex.getCode()).isEqualTo("KIND_NOT_ALLOWED_FOR_ROLE");
					});
			}
		}
	}

	@Test
	void audienceMustBeAllowedForTheKind() {
		policies.checkAudience(MARKETING, new Audience(List.of(CURRENT_PARENTS, ENQUIRY_OPEN), null));
		assertThatThrownBy(() -> policies.checkAudience(FEE_REMINDER, new Audience(List.of(ENQUIRY_OPEN), null)))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getStatus().value()).isEqualTo(409);
				assertThat(ex.getCode()).isEqualTo("AUDIENCE_NOT_ALLOWED");
				assertThat(ex.getMessage()).isEqualTo("A fee reminder message cannot go to enquiry open.");
			});
		// No group at all is refused too.
		assertThatThrownBy(() -> policies.checkAudience(NOTICE, new Audience(List.of(), null)))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("AUDIENCE_NOT_ALLOWED"));
	}

	@Test
	void theAppDoesNotStartWithAMissingOrDoublePolicy() {
		assertThatThrownBy(() -> new KindPolicies(List.of(new NoticePolicy())))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageStartingWith("No KindPolicy for ");
		assertThatThrownBy(() -> new KindPolicies(List.of(new MarketingPolicy(), new MarketingPolicy())))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageStartingWith("Two policies for MARKETING");
	}

}

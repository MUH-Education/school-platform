package com.muhjain.school.notification;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.service.notification.impl.BirthdayTodayResolver;
import com.muhjain.school.service.notification.impl.BusChildrenWithoutTapTodayResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two audience groups of the Phase 12 rules, on the small school of {@link NotificationTestBase}
 * (7 Oct 2026, Wednesday). Every child there is born on 14 May 2016 unless a test changes it.
 */
class RuleAudienceTest extends NotificationTestBase {

	@Autowired
	private BirthdayTodayResolver birthdays;

	@Autowired
	private BusChildrenWithoutTapTodayResolver withoutTap;

	private void bornOn(long studentId, String dob) {
		jdbc.update("update student set dob = ?::date where id = ?", dob, studentId);
	}

	private long studentId(String name) {
		return jdbc.queryForObject("select id from student where name = ?", Long.class, name);
	}

	private void tap(long studentId, String outcome) {
		jdbc.update("insert into boarding_event (student_id, route_id, service_date, event_type, outcome, occurred_at, "
				+ "recorded_by) values (?, ?, date '2026-10-07', 'BOARDED_MORNING', ?, now(), ?)", studentId, route4,
				outcome, anyUserId());
	}

	private List<Recipient> birthdaysToday() {
		return birthdays.resolve(AudienceGroup.BIRTHDAY_TODAY, AudienceFilter.NONE);
	}

	private List<Recipient> withoutTapToday() {
		return withoutTap.resolve(AudienceGroup.BUS_CHILDREN_WITHOUT_TAP_TODAY, AudienceFilter.NONE);
	}

	@Test
	void birthdayTodayIsOnlyTheBirthdayChildOfAnActiveFamily() {
		bornOn(siya, "2015-10-07");
		bornOn(dev, "2016-10-07");
		bornOn(studentId("Riya Malik"), "2016-10-07");   // has left the school
		bornOn(aryan, "2016-10-08");                     // tomorrow

		List<Recipient> found = birthdaysToday();
		assertThat(found).extracting(Recipient::personId).containsExactlyInAnyOrder(punia, sharma);
		// Siya's brother Aryan is not named in the wish.
		Recipient puniaFamily = found.stream().filter(r -> r.personId() == punia).findFirst().orElseThrow();
		assertThat(puniaFamily.studentIds()).containsExactly(siya);
		assertThat(puniaFamily.values()).containsEntry("Child name", "Siya");

		AudienceFilter class11 = new AudienceFilter(List.of("11"), null, null, null, null, null, null, null, null,
				null);
		assertThat(birthdays.resolve(AudienceGroup.BIRTHDAY_TODAY, class11)).extracting(Recipient::personId)
			.containsExactly(punia);
	}

	@Test
	void bornOn29FebruaryIsWishedOn28FebruaryOfAnOrdinaryYear() {
		bornOn(dev, "2016-02-29");

		clock.setInstant(Instant.parse("2027-02-28T00:30:00Z"));   // 2027 has no 29 February
		assertThat(birthdaysToday()).extracting(Recipient::personId).containsExactly(sharma);

		clock.setInstant(Instant.parse("2028-02-28T00:30:00Z"));   // 2028 has one: wait for it
		assertThat(birthdaysToday()).isEmpty();
		clock.setInstant(Instant.parse("2028-02-29T00:30:00Z"));
		assertThat(birthdaysToday()).extracting(Recipient::personId).containsExactly(sharma);
	}

	@Test
	void withoutTapIsTheChildrenNotTappedOnARouteThatHasStarted() {
		long stop = jdbc.queryForObject("select id from route_stop where route_id = ?", Long.class, route4);
		bus(dev, stop);

		// Nobody on Route 4 is tapped yet: the bus may not have run, so nobody is told anything.
		assertThat(withoutTapToday()).isEmpty();

		tap(aryan, "DONE");
		List<Recipient> found = withoutTapToday();
		assertThat(found).extracting(Recipient::personId).containsExactly(sharma);
		assertThat(found.getFirst().values()).containsEntry("Child name", "Dev").containsEntry("Route", "Route 4");

		// Marked ABSENT is an answer too: the attendant knows, so no "did not board" message.
		tap(dev, "ABSENT");
		assertThat(withoutTapToday()).isEmpty();
	}

	@Test
	void withoutTapIsNobodyOnADayWithoutSchool() {
		long stop = jdbc.queryForObject("select id from route_stop where route_id = ?", Long.class, route4);
		bus(dev, stop);
		tap(aryan, "DONE");
		jdbc.update("insert into holiday (holiday_date, name, created_by) values (date '2026-10-07', 'Test day', ?)",
				anyUserId());

		assertThat(withoutTapToday()).isEmpty();
	}

}

package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.muhjain.school.fee.FeeStatusCalculator.DueInput;
import com.muhjain.school.fee.FeeStatusCalculator.Summary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure Java, no Spring. Every state, with a fixed clock. Grace is 10 days, "defaulted" after 60 days
 * (the start values of the settings). The child owes school 30,000 and bus 8,800, quarterly, from 1 Apr 2026.
 */
class FeeStatusCalculatorTest {

	static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

	static final int GRACE = 10;

	static final int DEFAULTED_AFTER = 60;

	/** A fixed clock at 10:00 school time on the given day, read back as "today". */
	private static LocalDate on(String day) {
		LocalDate date = LocalDate.parse(day);
		Clock clock = Clock.fixed(date.atTime(10, 0).atZone(ZONE).toInstant(), ZONE);
		return LocalDate.now(clock);
	}

	private static BigDecimal rs(long amount) {
		return BigDecimal.valueOf(amount).setScale(2);
	}

	private static List<DueInput> quarterlyDues() {
		List<DueInput> dues = new ArrayList<>();
		long id = 1;
		for (String day : List.of("2026-04-01", "2026-07-01", "2026-10-01", "2027-01-01")) {
			dues.add(new DueInput(id++, FeeHead.SCHOOL, LocalDate.parse(day), rs(7500)));
			dues.add(new DueInput(id++, FeeHead.BUS, LocalDate.parse(day), rs(2200)));
		}
		return dues;
	}

	private static Summary calc(String today, long school, long bus) {
		return FeeStatusCalculator.calculate(quarterlyDues(), Map.of(FeeHead.SCHOOL, rs(school), FeeHead.BUS, rs(bus)),
				on(today), GRACE, DEFAULTED_AFTER);
	}

	@Test
	void sixDaysLateIsStillOnTime() {
		// The example of docs/03: 7 Oct, school dues 1 Apr, 1 Jul, 1 Oct, paid 15,000 → 1 Oct due is 6 days late.
		Summary summary = FeeStatusCalculator.calculate(
				quarterlyDues().stream().filter(d -> d.head() == FeeHead.SCHOOL).toList(),
				Map.of(FeeHead.SCHOOL, rs(15000)), on("2026-10-07"), GRACE, DEFAULTED_AFTER);

		assertThat(summary.status()).isEqualTo(FeeStatus.ON_TIME);
		assertThat(summary.heads().get(FeeHead.SCHOOL).daysLate()).isEqualTo(6);
		assertThat(summary.heads().get(FeeHead.SCHOOL).oldestUnpaidDue()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(summary.pendingNow()).isEqualByComparingTo("7500");
		assertThat(summary.remainingThisYear()).isEqualByComparingTo("15000");
	}

	@Test
	void thirtyDaysLateIsDelayed() {
		// 1 Apr and 1 Jul are paid. On 31 Oct the 1 Oct due is 30 days late.
		Summary summary = calc("2026-10-31", 15000, 4400);
		assertThat(summary.heads().get(FeeHead.SCHOOL).daysLate()).isEqualTo(30);
		assertThat(summary.status()).isEqualTo(FeeStatus.DELAYED);
	}

	@Test
	void seventyDaysLateIsDefaulted() {
		// 70 days after 1 Jul is 9 Sep. Only the 1 Apr due is paid.
		Summary summary = calc("2026-09-09", 7500, 2200);
		assertThat(summary.heads().get(FeeHead.SCHOOL).daysLate()).isEqualTo(70);
		assertThat(summary.status()).isEqualTo(FeeStatus.DEFAULTED);
	}

	@Test
	void theLimitsAreInclusive() {
		// Paid up to the 1 Apr due only. Oldest open due is 1 Jul.
		assertThat(calc("2026-07-11", 7500, 2200).status()).isEqualTo(FeeStatus.ON_TIME); // 10 days late
		assertThat(calc("2026-07-12", 7500, 2200).status()).isEqualTo(FeeStatus.DELAYED); // 11 days
		assertThat(calc("2026-08-30", 7500, 2200).status()).isEqualTo(FeeStatus.DELAYED); // 60 days
		assertThat(calc("2026-08-31", 7500, 2200).status()).isEqualTo(FeeStatus.DEFAULTED); // 61 days
	}

	@Test
	void paymentsCoverOldestDueFirst() {
		// The family paid 10,000 of school. It covers 1 Apr fully (7,500) and 2,500 of 1 Jul.
		Summary summary = calc("2026-07-05", 10000, 2200);

		List<FeeStatusCalculator.CoveredDue> school = summary.dues().stream().filter(d -> d.head() == FeeHead.SCHOOL).toList();
		assertThat(school.get(0).covered()).isEqualByComparingTo("7500");
		assertThat(school.get(1).covered()).isEqualByComparingTo("2500");
		assertThat(school.get(2).covered()).isEqualByComparingTo("0");
		// The oldest open due is 1 Jul (4 days late) → ON_TIME, and 5,000 of school is pending now.
		assertThat(summary.heads().get(FeeHead.SCHOOL).oldestUnpaidDue()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(summary.heads().get(FeeHead.SCHOOL).pendingNow()).isEqualByComparingTo("5000");
		assertThat(summary.status()).isEqualTo(FeeStatus.ON_TIME);
	}

	@Test
	void overallStatusIsTheWorseOfSchoolAndBus() {
		// School is fully paid to date, bus is not paid at all since April: bus is DEFAULTED, so is the child.
		Summary summary = calc("2026-10-07", 22500, 0);

		assertThat(summary.heads().get(FeeHead.SCHOOL).status()).isEqualTo(FeeStatus.ON_TIME);
		assertThat(summary.heads().get(FeeHead.BUS).status()).isEqualTo(FeeStatus.DEFAULTED);
		assertThat(summary.status()).isEqualTo(FeeStatus.DEFAULTED);
	}

	@Test
	void doneWhenNumbersOfPhase7() {
		// Admitted on 1 Apr with 30,000 + 8,800 quarterly and 9,700 paid.
		Summary onAdmission = calc("2026-04-01", 7500, 2200);
		assertThat(onAdmission.pendingNow()).isEqualByComparingTo("0");
		assertThat(onAdmission.remainingThisYear()).isEqualByComparingTo("29100");
		assertThat(onAdmission.nextDueOn()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(onAdmission.nextDueAmount()).isEqualByComparingTo("9700");
		assertThat(onAdmission.status()).isEqualTo(FeeStatus.ON_TIME);
		// 15 August with no second payment: the 1 Jul dues are 45 days late → DELAYED.
		assertThat(calc("2026-08-15", 7500, 2200).status()).isEqualTo(FeeStatus.DELAYED);
	}

	@Test
	void pendingNowAndRemainingOn7October() {
		// Dues 1 Apr, 1 Jul, 1 Oct of 7,500; paid 15,000 → pending now 7,500; remaining 15,000 (1 Oct + 1 Jan).
		Summary summary = FeeStatusCalculator.calculate(
				quarterlyDues().stream().filter(d -> d.head() == FeeHead.SCHOOL).toList(),
				Map.of(FeeHead.SCHOOL, rs(15000)), on("2026-10-07"), GRACE, DEFAULTED_AFTER);

		assertThat(summary.pendingNow()).isEqualByComparingTo("7500");
		assertThat(summary.remainingThisYear()).isEqualByComparingTo("15000");
		assertThat(summary.nextDueOn()).isEqualTo(LocalDate.of(2027, 1, 1));
		assertThat(summary.nextDueAmount()).isEqualByComparingTo("7500");
	}

	@Test
	void payingAheadLeavesNothingPendingAndShrinksTheNextDue() {
		// Paid 25,000 school and 6,600 bus on 7 Oct: 22,500 school is due so far, 2,500 goes on the January due.
		// January is left: school 5,000 + bus 2,200 = 7,200.
		Summary summary = calc("2026-10-07", 25000, 6600);

		assertThat(summary.pendingNow()).isEqualByComparingTo("0");
		assertThat(summary.status()).isEqualTo(FeeStatus.ON_TIME);
		assertThat(summary.nextDueOn()).isEqualTo(LocalDate.of(2027, 1, 1));
		assertThat(summary.nextDueAmount()).isEqualByComparingTo("7200");
		assertThat(summary.remainingThisYear()).isEqualByComparingTo("7200");
	}

	@Test
	void everythingPaidHasNoNextDue() {
		Summary summary = calc("2026-10-07", 30000, 8800);

		assertThat(summary.nextDueOn()).isNull();
		assertThat(summary.nextDueAmount()).isNull();
		assertThat(summary.remainingThisYear()).isEqualByComparingTo("0");
		assertThat(summary.status()).isEqualTo(FeeStatus.ON_TIME);
	}

	@Test
	void childWithNoDuesAndNoBusIsOnTime() {
		Summary summary = FeeStatusCalculator.calculate(List.of(), Map.of(), on("2026-10-07"), GRACE,
				DEFAULTED_AFTER);

		assertThat(summary.status()).isEqualTo(FeeStatus.ON_TIME);
		assertThat(summary.pendingNow()).isEqualByComparingTo("0");
		assertThat(summary.heads().get(FeeHead.BUS).status()).isEqualTo(FeeStatus.ON_TIME);
	}

	@Test
	void fixedClockHelperReadsSchoolDayNotUtcDay() {
		// 00:30 on 8 Oct school time is still 7 Oct in UTC. The school day is 8 Oct.
		Clock clock = Clock.fixed(Instant.parse("2026-10-07T19:00:00Z"), ZONE);
		assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 10, 8));
	}

}

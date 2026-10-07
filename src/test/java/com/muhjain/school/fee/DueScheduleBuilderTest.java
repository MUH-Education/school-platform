package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.muhjain.school.fee.DueScheduleBuilder.Due;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure Java, no Spring. Examples A and B come from phase-7-fees.md. */
class DueScheduleBuilderTest {

	static final LocalDate START = LocalDate.of(2026, 4, 1);

	static final LocalDate END = LocalDate.of(2027, 3, 31);

	private static BigDecimal rs(long amount) {
		return BigDecimal.valueOf(amount).setScale(2);
	}

	private static BigDecimal total(List<Due> dues, FeeHead head) {
		return dues.stream().filter(d -> d.head() == head).map(Due::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	@Test
	void quarterlyPlanMakesFourEqualDues() {
		// Example A: school 30,000 and bus 8,800, quarterly, from 1 Apr.
		List<Due> dues = DueScheduleBuilder.build(rs(30000), rs(8800), PayFrequency.QUARTERLY, START, END, START);

		assertThat(dues).hasSize(8);
		assertThat(dues).filteredOn(d -> d.head() == FeeHead.SCHOOL).extracting(Due::dueOn)
			.containsExactly(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 10, 1),
					LocalDate.of(2027, 1, 1));
		assertThat(dues).filteredOn(d -> d.head() == FeeHead.SCHOOL).extracting(Due::amount)
			.containsOnly(rs(7500));
		assertThat(dues).filteredOn(d -> d.head() == FeeHead.BUS).extracting(Due::amount).containsOnly(rs(2200));
		// Each quarter the family owes 9,700.
		assertThat(dues.stream().filter(d -> d.dueOn().equals(LocalDate.of(2026, 7, 1))).map(Due::amount)
			.reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("9700");
	}

	@Test
	void monthlyPlanMakesTwelveDuesAndLastOneTakesTheRest() {
		// 10,000 / 12 = 833 in whole rupees. 11 × 833 = 9,163. The last due is 837.
		List<Due> dues = DueScheduleBuilder.build(FeeHead.SCHOOL, rs(10000), PayFrequency.MONTHLY, START, END, START);

		assertThat(dues).hasSize(12);
		assertThat(dues.get(0).dueOn()).isEqualTo(LocalDate.of(2026, 4, 1));
		assertThat(dues.get(11).dueOn()).isEqualTo(LocalDate.of(2027, 3, 1));
		assertThat(dues.subList(0, 11)).extracting(Due::amount).containsOnly(rs(833));
		assertThat(dues.get(11).amount()).isEqualTo(rs(837));
		assertThat(total(dues, FeeHead.SCHOOL)).isEqualByComparingTo("10000");
	}

	@Test
	void midYearJoinStartsOnJoiningDate() {
		// Example B: joins on 2 Nov, school fee for the rest of the year 12,000, quarterly.
		List<Due> dues = DueScheduleBuilder.build(FeeHead.SCHOOL, rs(12000), PayFrequency.QUARTERLY, START, END,
				LocalDate.of(2026, 11, 2));

		assertThat(dues).extracting(Due::dueOn).containsExactly(LocalDate.of(2026, 11, 2), LocalDate.of(2027, 1, 1));
		assertThat(dues).extracting(Due::amount).containsExactly(rs(6000), rs(6000));
	}

	@Test
	void joiningOnAStandardDateGivesNoDoubleDue() {
		List<Due> dues = DueScheduleBuilder.build(FeeHead.BUS, rs(4000), PayFrequency.QUARTERLY, START, END,
				LocalDate.of(2026, 10, 1));

		assertThat(dues).extracting(Due::dueOn).containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 1, 1));
	}

	@Test
	void busStartingInNovemberQuarterly() {
		// Rule 8: bus starts 2 Nov, 4,000, quarterly → 2,000 on 2 Nov and 2,000 on 1 Jan.
		List<Due> dues = DueScheduleBuilder.build(FeeHead.BUS, rs(4000), PayFrequency.QUARTERLY, START, END,
				LocalDate.of(2026, 11, 2));

		assertThat(dues).extracting(Due::amount).containsExactly(rs(2000), rs(2000));
	}

	@Test
	void yearlyHasOneDueAndAJoiningBeforeTheSessionStartsUsesTheSessionStart() {
		assertThat(DueScheduleBuilder.build(FeeHead.SCHOOL, rs(30000), PayFrequency.YEARLY, START, END, START))
			.containsExactly(new Due(FeeHead.SCHOOL, START, rs(30000)));
		assertThat(DueScheduleBuilder.build(FeeHead.SCHOOL, rs(30000), PayFrequency.YEARLY, START, END,
				LocalDate.of(2026, 3, 20)))
			.extracting(Due::dueOn)
			.containsExactly(START);
		// Joining in July, yearly: one due, on the joining day.
		assertThat(DueScheduleBuilder.build(FeeHead.SCHOOL, rs(20000), PayFrequency.YEARLY, START, END,
				LocalDate.of(2026, 7, 10)))
			.extracting(Due::dueOn)
			.containsExactly(LocalDate.of(2026, 7, 10));
	}

	@Test
	void monthlyJoinInMarchHasOneDue() {
		assertThat(DueScheduleBuilder.build(FeeHead.SCHOOL, rs(900), PayFrequency.MONTHLY, START, END,
				LocalDate.of(2027, 3, 15)))
			.extracting(Due::dueOn)
			.containsExactly(LocalDate.of(2027, 3, 15));
	}

	@Test
	void totalIsAlwaysExactAndNoDueIsBelowOne() {
		// 7 rupees over 12 months: 0 in whole rupees for eleven dues, so the dues of 0 are left out and the last has 7.
		List<Due> dues = DueScheduleBuilder.build(FeeHead.SCHOOL, rs(7), PayFrequency.MONTHLY, START, END, START);

		assertThat(total(dues, FeeHead.SCHOOL)).isEqualByComparingTo("7");
		assertThat(dues).allMatch(d -> d.amount().signum() > 0);
		// Paise stay exact too.
		List<Due> withPaise = DueScheduleBuilder.build(FeeHead.SCHOOL, new BigDecimal("100.50"),
				PayFrequency.QUARTERLY, START, END, START);
		assertThat(total(withPaise, FeeHead.SCHOOL)).isEqualByComparingTo("100.50");
	}

	@Test
	void zeroBusFeeMakesNoBusDues() {
		List<Due> dues = DueScheduleBuilder.build(rs(30000), BigDecimal.ZERO, PayFrequency.QUARTERLY, START, END,
				START);

		assertThat(dues).hasSize(4).allMatch(d -> d.head() == FeeHead.SCHOOL);
	}

	@Test
	void startAfterTheSessionEndsIsAnError() {
		assertThatThrownBy(() -> DueScheduleBuilder.build(FeeHead.SCHOOL, rs(1000), PayFrequency.YEARLY, START, END,
				LocalDate.of(2027, 4, 1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

}

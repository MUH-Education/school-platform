package com.muhjain.school.analytics;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.muhjain.school.analytics.MonthlyCollectionCalculator.MonthRow;
import com.muhjain.school.fee.FeeHead;
import com.muhjain.school.fee.FeeStatusCalculator.CoveredDue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyCollectionCalculatorTest {

	// 7 Oct 2026, 10:00 school time.
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-07T04:30:00Z"), ZoneId.of("Asia/Kolkata"));

	private static final LocalDate START = LocalDate.of(2026, 4, 1);

	private static final LocalDate END = LocalDate.of(2027, 3, 31);

	private static CoveredDue due(FeeHead head, String on, long amount, long covered) {
		return new CoveredDue(null, head, LocalDate.parse(on), BigDecimal.valueOf(amount), BigDecimal.valueOf(covered));
	}

	@Test
	void monthlyCollectionIs96PercentInTheExample() {
		// April dues ₹10,00,000 (two families), ₹9,60,000 of them covered.
		List<CoveredDue> dues = List.of(due(FeeHead.SCHOOL, "2026-04-01", 600_000, 600_000),
				due(FeeHead.SCHOOL, "2026-04-01", 400_000, 360_000));

		List<MonthRow> rows = MonthlyCollectionCalculator.calculate(dues, START, END, LocalDate.now(clock));

		MonthRow april = rows.get(0);
		assertThat(april.month()).isEqualTo("2026-04");
		assertThat(april.school().due()).isEqualByComparingTo("1000000");
		assertThat(april.school().collected()).isEqualByComparingTo("960000");
		assertThat(april.school().percent()).isEqualByComparingTo("96.0");
	}

	@Test
	void monthsRunFromTheStartOfTheYearToThisMonthOnly() {
		List<MonthRow> rows = MonthlyCollectionCalculator.calculate(List.of(), START, END, LocalDate.now(clock));
		assertThat(rows).extracting(MonthRow::month)
			.containsExactly("2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10");
	}

	@Test
	void aFinishedYearShowsAllTwelveMonthsAndAFutureYearShowsNone() {
		assertThat(MonthlyCollectionCalculator.calculate(List.of(), START, END, LocalDate.of(2027, 6, 1))).hasSize(12);
		assertThat(MonthlyCollectionCalculator.calculate(List.of(), START, END, LocalDate.of(2026, 3, 31))).isEmpty();
	}

	@Test
	void aMonthWithNoDuesForAHeadHasNoPercent() {
		List<CoveredDue> dues = List.of(due(FeeHead.SCHOOL, "2026-04-01", 7500, 7500));
		MonthRow may = MonthlyCollectionCalculator.calculate(dues, START, END, LocalDate.now(clock)).get(1);
		assertThat(may.school().percent()).isNull();
		assertThat(may.school().due()).isEqualByComparingTo("0");
		assertThat(may.bus().percent()).isNull();
	}

	@Test
	void headsAreKeptApartAndPercentRoundsHalfUp() {
		List<CoveredDue> dues = List.of(due(FeeHead.SCHOOL, "2026-07-01", 3, 2), due(FeeHead.BUS, "2026-07-15", 200, 50));
		MonthRow july = MonthlyCollectionCalculator.calculate(dues, START, END, LocalDate.now(clock)).get(3);
		assertThat(july.school().percent()).isEqualByComparingTo("66.7");
		assertThat(july.bus().percent()).isEqualByComparingTo("25.0");
	}

}

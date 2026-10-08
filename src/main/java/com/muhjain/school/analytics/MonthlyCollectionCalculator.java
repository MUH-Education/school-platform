package com.muhjain.school.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.muhjain.school.fee.FeeHead;
import com.muhjain.school.fee.FeeStatusCalculator.CoveredDue;

/**
 * Rule 5 of phase 8: for each month of the school year, up to this month, and for each fee head, how much of that
 * month's dues is covered. Pure Java: no Spring, no database, no clock. The caller gives "today".
 * <p>
 * The covering (payments cover the oldest dues first) is already done by {@code FeeStatusCalculator}; each
 * {@link CoveredDue} says how much of it is covered. This class only adds up and divides.
 * <p>
 * Example: April dues ₹10,00,000 and ₹9,60,000 of them covered → April {@code percent = 96.0}.
 * A month where a head has no dues has {@code percent = null} (not 0): there was nothing to collect.
 */
public final class MonthlyCollectionCalculator {

	/** One head in one month. */
	public record HeadCollection(BigDecimal due, BigDecimal collected, BigDecimal percent) {
	}

	/** One month. {@code month} looks like "2026-04". */
	public record MonthRow(String month, HeadCollection school, HeadCollection bus) {
	}

	private MonthlyCollectionCalculator() {
	}

	/**
	 * @param dues the dues of every child in the report, both heads, with the covered part
	 * @param sessionStart first day of the school year, example 1 Apr 2026
	 * @param sessionEnd last day of the school year, example 31 Mar 2027
	 * @param today months after this one are left out; before the school year starts the answer is empty
	 */
	public static List<MonthRow> calculate(Collection<CoveredDue> dues, LocalDate sessionStart, LocalDate sessionEnd,
			LocalDate today) {
		YearMonth last = YearMonth.from(today);
		if (YearMonth.from(sessionEnd).isBefore(last)) {
			last = YearMonth.from(sessionEnd);
		}
		List<MonthRow> rows = new ArrayList<>();
		for (YearMonth month = YearMonth.from(sessionStart); !month.isAfter(last); month = month.plusMonths(1)) {
			rows.add(new MonthRow(month.toString(), head(dues, FeeHead.SCHOOL, month), head(dues, FeeHead.BUS, month)));
		}
		return rows;
	}

	private static HeadCollection head(Collection<CoveredDue> dues, FeeHead head, YearMonth month) {
		CollectionMath.Totals totals = CollectionMath.between(dues, head, month.atDay(1), month.atEndOfMonth());
		BigDecimal percent = (totals.due().signum() == 0) ? null : CollectionMath.percent(totals);
		return new HeadCollection(totals.due(), totals.covered(), percent);
	}

}

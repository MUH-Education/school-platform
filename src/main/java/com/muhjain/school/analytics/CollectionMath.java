package com.muhjain.school.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;

import com.muhjain.school.fee.FeeHead;
import com.muhjain.school.fee.FeeStatusCalculator.CoveredDue;

/**
 * The one place that turns "dues and the part covered" into "percent collected". Pure Java: no Spring, no clock.
 * The covering itself (payments cover the oldest dues first) is done by {@code FeeStatusCalculator}, not here.
 * Example: dues ₹10,00,000, covered ₹9,60,000 → 96.0.
 */
final class CollectionMath {

	/** Money due and the part of it that payments cover. */
	record Totals(BigDecimal due, BigDecimal covered) {

		static final Totals ZERO = new Totals(BigDecimal.ZERO, BigDecimal.ZERO);

		Totals plus(Totals other) {
			return new Totals(due.add(other.due), covered.add(other.covered));
		}

	}

	private CollectionMath() {
	}

	/**
	 * The dues of one head that fall on or before {@code lastDay}.
	 * Example on 7 Oct: dues of 1,000 on 1 Apr … 1 Mar, the first 7 covered → due 7,000, covered 7,000.
	 */
	static Totals upTo(Collection<CoveredDue> dues, FeeHead head, LocalDate lastDay) {
		return between(dues, head, LocalDate.MIN, lastDay);
	}

	/** The dues of one head with a due date from {@code first} to {@code last}, both days included. */
	static Totals between(Collection<CoveredDue> dues, FeeHead head, LocalDate first, LocalDate last) {
		BigDecimal due = BigDecimal.ZERO;
		BigDecimal covered = BigDecimal.ZERO;
		for (CoveredDue d : dues) {
			if (d.head() == head && !d.dueOn().isBefore(first) && !d.dueOn().isAfter(last)) {
				due = due.add(d.amount());
				covered = covered.add(d.covered());
			}
		}
		return new Totals(due, covered);
	}

	/** Covered ÷ due as a percent with one decimal, rounded half up. Nothing due → 0. */
	static BigDecimal percent(Totals totals) {
		return percent(totals.covered(), totals.due());
	}

	static BigDecimal percent(BigDecimal part, BigDecimal whole) {
		if (whole.signum() == 0) {
			return BigDecimal.ZERO.setScale(1);
		}
		return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
	}

}

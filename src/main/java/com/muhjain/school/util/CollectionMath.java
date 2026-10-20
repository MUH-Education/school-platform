package com.muhjain.school.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;

import com.muhjain.school.model.enums.FeeHead;
import com.muhjain.school.util.FeeStatusCalculator.MonthInput;

/**
 * The one place that turns "due and the part covered" into "percent collected". Pure Java: no Spring, no clock.
 * Example: dues ₹10,00,000, covered ₹9,60,000 → 96.0.
 */
public final class CollectionMath {

	/** Money due and the part of it that payments cover. */
	public record Totals(BigDecimal due, BigDecimal covered) {

		public static final Totals ZERO = new Totals(BigDecimal.ZERO, BigDecimal.ZERO);

		public Totals plus(Totals other) {
			return new Totals(due.add(other.due), covered.add(other.covered));
		}

	}

	private CollectionMath() {
	}

	/**
	 * The dues of one head whose due date falls on or before {@code lastDay}.
	 * Example on 7 Oct: dues of 1,000 on 1 Apr … 1 Mar, the first 7 covered → due 7,000, covered 7,000.
	 */
	public static Totals upTo(Collection<MonthInput> months, FeeHead head, LocalDate lastDay) {
		return between(months, head, LocalDate.MIN, lastDay);
	}

	/** The dues of one head with a due date from {@code first} to {@code last}, both days included. */
	public static Totals between(Collection<MonthInput> months, FeeHead head, LocalDate first, LocalDate last) {
		BigDecimal due = BigDecimal.ZERO;
		BigDecimal covered = BigDecimal.ZERO;
		for (MonthInput m : months) {
			if (m.dueDate().isBefore(first) || m.dueDate().isAfter(last)) {
				continue;
			}
			if (head == FeeHead.SCHOOL) {
				due = due.add(m.schoolAmount().subtract(m.discountAmount()));
				covered = covered.add(m.paidSchool());
			}
			else if (head == FeeHead.BUS) {
				due = due.add(m.busAmount());
				covered = covered.add(m.paidBus());
			}
		}
		return new Totals(due, covered);
	}

	/** Covered ÷ due as a percent with one decimal, rounded half up. Nothing due → 0. */
	public static BigDecimal percent(Totals totals) {
		return percent(totals.covered(), totals.due());
	}

	public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
		if (whole.signum() == 0) {
			return BigDecimal.ZERO.setScale(1);
		}
		return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
	}

}

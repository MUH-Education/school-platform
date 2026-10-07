package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Makes the dues of a fee plan (rule 6 of phase 7). Pure Java: no Spring, no database, no clock.
 * <p>
 * Standard due dates, counted from the first day of the session (1 Apr):
 * YEARLY → 1 Apr. QUARTERLY → 1 Apr, 1 Jul, 1 Oct, 1 Jan. MONTHLY → the 1st of each month, April to March.
 * A child who joins later: the first due date is the joining day, then the standard dates after it.
 * Each fee head is split equally over its dates in whole rupees. The last due takes the rest, so the total is exact.
 * <p>
 * Example A: school ₹30,000 and bus ₹8,800, QUARTERLY, from 1 Apr → SCHOOL 4 × ₹7,500 and BUS 4 × ₹2,200.
 * <br>
 * Example B: joins 2 Nov, school ₹12,000, QUARTERLY → dues on 2 Nov and 1 Jan, 2 × ₹6,000.
 */
public final class DueScheduleBuilder {

	/** One due before it is saved. Example: SCHOOL, 1 Jul 2026, 7500. */
	public record Due(FeeHead head, LocalDate dueOn, BigDecimal amount) {
	}

	private DueScheduleBuilder() {
	}

	/**
	 * The dues of both fee heads, oldest first (SCHOOL before BUS on the same day).
	 *
	 * @param schoolFee the school fee after the discount
	 * @param busFee the bus fee for the year, 0 for no bus
	 * @param from the first day the family owes money: the joining day, or the session start
	 */
	public static List<Due> build(BigDecimal schoolFee, BigDecimal busFee, PayFrequency frequency,
			LocalDate sessionStart, LocalDate sessionEnd, LocalDate from) {
		List<Due> dues = new ArrayList<>(build(FeeHead.SCHOOL, schoolFee, frequency, sessionStart, sessionEnd, from));
		dues.addAll(build(FeeHead.BUS, busFee, frequency, sessionStart, sessionEnd, from));
		dues.sort(Comparator.comparing(Due::dueOn).thenComparing(Due::head));
		return dues;
	}

	/**
	 * The dues of one fee head. A total of 0 gives no dues.
	 * Example: 8800, QUARTERLY, session 1 Apr 2026 to 31 Mar 2027, from 1 Apr → 4 × 2200.
	 *
	 * @throws IllegalArgumentException if {@code from} is after the end of the session or the total is below 0
	 */
	public static List<Due> build(FeeHead head, BigDecimal total, PayFrequency frequency, LocalDate sessionStart,
			LocalDate sessionEnd, LocalDate from) {
		if (total.signum() < 0) {
			throw new IllegalArgumentException("The total cannot be below 0.");
		}
		if (from.isAfter(sessionEnd)) {
			throw new IllegalArgumentException("The start day is after the end of the session.");
		}
		if (total.signum() == 0) {
			return List.of();
		}
		List<LocalDate> dates = dueDates(frequency, sessionStart, sessionEnd, from);
		return split(head, total, dates);
	}

	/** The days on which money is due. Example: QUARTERLY, joins 2 Nov → 2 Nov, 1 Jan. */
	static List<LocalDate> dueDates(PayFrequency frequency, LocalDate sessionStart, LocalDate sessionEnd,
			LocalDate from) {
		int step = switch (frequency) {
			case YEARLY -> 12;
			case QUARTERLY -> 3;
			case MONTHLY -> 1;
		};
		List<LocalDate> dates = new ArrayList<>();
		LocalDate first = from.isAfter(sessionStart) ? from : sessionStart;
		dates.add(first);
		for (int months = 0; months < 12; months += step) {
			LocalDate standard = sessionStart.plusMonths(months);
			if (standard.isAfter(first) && !standard.isAfter(sessionEnd)) {
				dates.add(standard);
			}
		}
		return dates;
	}

	// Whole rupees for every due but the last. Rounding down keeps the last one from going below the others.
	private static List<Due> split(FeeHead head, BigDecimal total, List<LocalDate> dates) {
		int count = dates.size();
		BigDecimal each = total.divide(BigDecimal.valueOf(count), 0, RoundingMode.DOWN);
		List<Due> dues = new ArrayList<>(count);
		BigDecimal given = BigDecimal.ZERO;
		for (int i = 0; i < count; i++) {
			BigDecimal amount = (i == count - 1) ? total.subtract(given) : each;
			given = given.add(amount);
			if (amount.signum() > 0) {
				dues.add(new Due(head, dates.get(i), amount.setScale(2, RoundingMode.UNNECESSARY)));
			}
		}
		return dues;
	}

}

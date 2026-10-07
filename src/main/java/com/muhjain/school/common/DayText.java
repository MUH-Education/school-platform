package com.muhjain.school.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Dates as short English text, for messages and the change history.
 * Example: {@code on(2026-10-12)} → "12 Oct 2026". {@code range(2026-10-12, 2026-10-16)} → "12 to 16 Oct".
 */
public final class DayText {

	private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d", Locale.ENGLISH);

	private DayText() {
	}

	/** Example: "12 Oct 2026". */
	public static String on(LocalDate date) {
		return FULL.format(date);
	}

	/**
	 * Two days. The same month → "12 to 16 Oct". Another month → "28 Oct to 2 Nov".
	 * Another year → both with the year, "28 Dec 2026 to 2 Jan 2027".
	 */
	public static String range(LocalDate from, LocalDate to) {
		if (from.getYear() != to.getYear()) {
			return on(from) + " to " + on(to);
		}
		if (from.getMonth() != to.getMonth()) {
			return DAY_MONTH.format(from) + " to " + DAY_MONTH.format(to);
		}
		return DAY.format(from) + " to " + DAY_MONTH.format(to);
	}

}

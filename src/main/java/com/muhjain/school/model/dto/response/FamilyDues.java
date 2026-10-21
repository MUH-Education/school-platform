package com.muhjain.school.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What one family owes right now, all its children added up (this session, as of today). Read live, never stored.
 * Example: Punia family on 7 Oct 2026: amount 8500, months [Sep, Oct], 1 month late, late fee 100.
 *
 * @param amount money due now and not paid ({@code pendingNow} of each child)
 * @param dueMonths the months with money due and not paid, in order (first day of each month)
 * @param monthsLate how many of them are past their grace day
 * @param lateFee the late fee if the family paid today (the session's rule: per month, or per day with a cap)
 */
public record FamilyDues(BigDecimal amount, List<LocalDate> dueMonths, int monthsLate, BigDecimal lateFee) {

	public static final FamilyDues NONE = new FamilyDues(BigDecimal.ZERO, List.of(), 0, BigDecimal.ZERO);

	public FamilyDues {
		dueMonths = List.copyOf(dueMonths);
	}

}

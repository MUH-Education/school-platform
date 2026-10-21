package com.muhjain.school.model.dto.response;

import com.muhjain.school.util.FeeStatusCalculator.MonthView;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One month's due with what is paid. Example: July 2026, total 3300, paid 800, open 2500, due, not yet late.
 */
public record DueView(Long feeDueId, LocalDate month, BigDecimal total, BigDecimal paid, BigDecimal open,
		LocalDate dueDate, LocalDate graceDate, boolean due, boolean late) {

	public static DueView of(MonthView month) {
		return new DueView(month.feeDueId(), month.month(), month.total(), month.paid(), month.balance(),
				month.dueDate(), month.graceDate(), month.due(), month.late());
	}

}

package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.muhjain.school.fee.FeeStatusCalculator.CoveredDue;

/**
 * One due with the part payments cover. Example: SCHOOL due 1 Jul, amount 7500, covered 2500, open 5000.
 * {@code overdue} is true when the day has come and something is still open.
 */
public record DueView(Long id, FeeHead feeHead, LocalDate dueOn, BigDecimal amount, BigDecimal covered,
		BigDecimal open, boolean overdue) {

	static DueView of(CoveredDue due, LocalDate today) {
		BigDecimal open = due.amount().subtract(due.covered());
		return new DueView(due.id(), due.head(), due.dueOn(), due.amount(), due.covered(), open,
				open.signum() > 0 && !due.dueOn().isAfter(today));
	}

}

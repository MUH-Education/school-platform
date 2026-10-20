package com.muhjain.school.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.muhjain.school.util.FeeStatusCalculator.CoveredDue;
import com.muhjain.school.model.enums.FeeHead;
import com.muhjain.school.util.FeeStatusCalculator;

/**
 * One due with the part payments cover. Example: SCHOOL due 1 Jul, amount 7500, covered 2500, open 5000.
 * {@code overdue} is true when the day has come and something is still open.
 */
public record DueView(Long id, FeeHead feeHead, LocalDate dueOn, BigDecimal amount, BigDecimal covered,
		BigDecimal open, boolean overdue) {

	public static DueView of(CoveredDue due, LocalDate today) {
		BigDecimal open = due.amount().subtract(due.covered());
	return new DueView(due.id(), due.head(), due.dueOn(), due.amount(), due.covered(), open,
				open.signum() > 0 && !due.dueOn().isAfter(today));
	}

}

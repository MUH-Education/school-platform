package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.muhjain.school.fee.FeeStatusCalculator.HeadStatus;

/**
 * The status of one fee head. Example: SCHOOL, DELAYED, 45 days late since 1 Jul, 7500 pending now.
 * {@code oldestUnpaidDue} is null when nothing is pending now.
 */
public record HeadView(FeeHead feeHead, FeeStatus status, BigDecimal paid, BigDecimal pendingNow,
		BigDecimal remaining, LocalDate oldestUnpaidDue, long daysLate) {

	static HeadView of(HeadStatus head) {
		return new HeadView(head.head(), head.status(), head.paid(), head.pendingNow(), head.remaining(),
				head.oldestUnpaidDue(), head.daysLate());
	}

}

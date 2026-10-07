package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One due. Example: {@code {"id":12,"feeHead":"SCHOOL","dueOn":"2026-07-01","amount":7500.00}} */
public record DueResponse(Long id, FeeHead feeHead, LocalDate dueOn, BigDecimal amount) {

	static DueResponse of(FeeDue due) {
		return new DueResponse(due.getId(), due.getFeeHead(), due.getDueOn(), due.getAmount());
	}

}

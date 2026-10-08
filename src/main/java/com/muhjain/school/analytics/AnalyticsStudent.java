package com.muhjain.school.analytics;

import java.math.BigDecimal;

import com.muhjain.school.fee.FeeHead;
import com.muhjain.school.fee.FeeStatus;
import com.muhjain.school.fee.FeeStatusCalculator;
import com.muhjain.school.student.FatherOccupation;

/**
 * One child in an Analytics answer, with the fee numbers of the chosen school year. {@code fee} is the result of
 * {@link FeeStatusCalculator}, the same one the child's own fee page uses. It is null when the child has no fee plan
 * that year.
 * Example: Aryan, class 3, Jakhal, Route 4, DELAYED, pending ₹7,500.
 */
public record AnalyticsStudent(Long id, String name, String className, String section, String village,
		FatherOccupation occupation, Long routeId, String routeName, FeeStatusCalculator.Summary fee) {

	public boolean usesBus() {
		return routeId != null;
	}

	/** ON_TIME, DELAYED or DEFAULTED; null with no fee plan. */
	public FeeStatus feeStatus() {
		return (fee == null) ? null : fee.status();
	}

	/** Status of one head; null with no fee plan. */
	public FeeStatus headStatus(FeeHead head) {
		return (fee == null) ? null : fee.heads().get(head).status();
	}

	/** Dues up to today that payments do not cover, both heads. Zero with no fee plan. */
	public BigDecimal pendingNow() {
		return (fee == null) ? BigDecimal.ZERO : fee.pendingNow();
	}

}

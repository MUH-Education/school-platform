package com.muhjain.school.fee;

import java.math.BigDecimal;

/** The plan without its dues (the dues come with the coverage). Example: school 30000, bus 8800, QUARTERLY. */
public record FeePlanSummary(Long id, BigDecimal schoolFee, BigDecimal busFee, BigDecimal discount,
		DiscountReason discountReason, BigDecimal netSchoolFee, PayFrequency payFrequency) {

	static FeePlanSummary of(FeePlan plan) {
		return new FeePlanSummary(plan.getId(), plan.getSchoolFee(), plan.getBusFee(), plan.getDiscount(),
				plan.getDiscountReason(), plan.getSchoolFee().subtract(plan.getDiscount()), plan.getPayFrequency());
	}

}

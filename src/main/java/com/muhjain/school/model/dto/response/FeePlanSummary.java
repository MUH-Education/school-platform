package com.muhjain.school.model.dto.response;

import java.math.BigDecimal;
import com.muhjain.school.model.entity.FeePlan;
import com.muhjain.school.model.enums.DiscountReason;
import com.muhjain.school.model.enums.PayFrequency;

/** The plan without its dues (the dues come with the coverage). Example: school 30000, bus 8800, QUARTERLY. */
public record FeePlanSummary(Long id, BigDecimal schoolFee, BigDecimal busFee, BigDecimal discount,
		DiscountReason discountReason, BigDecimal netSchoolFee, PayFrequency payFrequency) {

	public static FeePlanSummary of(FeePlan plan) {
	return new FeePlanSummary(plan.getId(), plan.getSchoolFee(), plan.getBusFee(), plan.getDiscount(),
				plan.getDiscountReason(), plan.getSchoolFee().subtract(plan.getDiscount()), plan.getPayFrequency());
	}

}

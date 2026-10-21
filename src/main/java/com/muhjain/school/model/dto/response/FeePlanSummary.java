package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.entity.FeePlan;
import com.muhjain.school.model.enums.DiscountReason;
import com.muhjain.school.model.enums.PayFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The current time slice of the plan, without its dues. Example: school 2500 a month, from April, MONTHLY. */
public record FeePlanSummary(Long id, LocalDate fromMonth, BigDecimal schoolFeePerMonth,
		BigDecimal discountPerMonth, DiscountReason discountReason, BigDecimal netSchoolFeePerMonth,
		PayFrequency payFrequency) {

	public static FeePlanSummary of(FeePlan plan) {
		return new FeePlanSummary(plan.getId(), plan.getFromMonth(), plan.getSchoolFeePerMonth(),
				plan.getDiscountPerMonth(), plan.getDiscountReason(),
				plan.getSchoolFeePerMonth().subtract(plan.getDiscountPerMonth()), plan.getPayFrequency());
	}

}

package com.muhjain.school.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.muhjain.school.model.entity.FeeDue;
import com.muhjain.school.model.entity.FeePlan;
import com.muhjain.school.model.enums.DiscountReason;
import com.muhjain.school.model.enums.PayFrequency;

/**
 * The newest slice of a fee plan, with its dues (every month from its own schedule, not just this slice).
 * {@code netSchoolFeePerMonth} is the school fee after the discount, a month.
 */
public record FeePlanResponse(Long id, Long studentId, Long sessionId, String sessionName, LocalDate fromMonth,
		BigDecimal schoolFeePerMonth, BigDecimal discountPerMonth, DiscountReason discountReason,
		BigDecimal netSchoolFeePerMonth, PayFrequency payFrequency, List<DueResponse> dues) {

	public static FeePlanResponse of(FeePlan plan, String sessionName, List<FeeDue> dues) {
		return new FeePlanResponse(plan.getId(), plan.getStudentId(), plan.getSessionId(), sessionName,
				plan.getFromMonth(), plan.getSchoolFeePerMonth(), plan.getDiscountPerMonth(),
				plan.getDiscountReason(), plan.getSchoolFeePerMonth().subtract(plan.getDiscountPerMonth()),
				plan.getPayFrequency(), dues.stream().map(DueResponse::of).toList());
	}

}

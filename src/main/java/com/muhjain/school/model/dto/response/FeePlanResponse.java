package com.muhjain.school.model.dto.response;

import java.math.BigDecimal;
import java.util.List;
import com.muhjain.school.model.entity.FeeDue;
import com.muhjain.school.model.entity.FeePlan;
import com.muhjain.school.model.enums.DiscountReason;
import com.muhjain.school.model.enums.PayFrequency;

/**
 * A fee plan with its dues. {@code netSchoolFee} is the school fee after the discount.
 * Example: school 30000, discount 2000 → netSchoolFee 28000.
 */
public record FeePlanResponse(Long id, Long studentId, Long sessionId, String sessionName, BigDecimal schoolFee,
		BigDecimal busFee, BigDecimal discount, DiscountReason discountReason, BigDecimal netSchoolFee,
		PayFrequency payFrequency, List<DueResponse> dues) {

	public static FeePlanResponse of(FeePlan plan, String sessionName, List<FeeDue> dues) {
	return new FeePlanResponse(plan.getId(), plan.getStudentId(), plan.getSessionId(), sessionName,
				plan.getSchoolFee(), plan.getBusFee(), plan.getDiscount(), plan.getDiscountReason(),
				plan.getSchoolFee().subtract(plan.getDiscount()), plan.getPayFrequency(),
				dues.stream().map(DueResponse::of).toList());
	}

}

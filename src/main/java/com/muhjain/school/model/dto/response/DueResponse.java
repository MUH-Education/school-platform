package com.muhjain.school.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.muhjain.school.model.entity.FeeDue;

/** One month's due. Example: {@code {"id":12,"month":"2026-07-01","schoolAmount":2500,"discountAmount":0,"busAmount":800,"total":3300}} */
public record DueResponse(Long id, LocalDate month, BigDecimal schoolAmount, BigDecimal discountAmount,
		BigDecimal busAmount, BigDecimal total) {

	static DueResponse of(FeeDue due) {
		return new DueResponse(due.getId(), due.getMonth(), due.getSchoolAmount(), due.getDiscountAmount(),
				due.getBusAmount(), due.getTotal());
	}

}

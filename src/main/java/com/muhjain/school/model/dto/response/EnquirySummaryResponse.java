package com.muhjain.school.model.dto.response;

import java.util.List;
import java.util.Map;
import com.muhjain.school.model.enums.EnquiryStatus;

/**
 * The numbers above the enquiry list (rule 9). {@code byStatus} has all six stages, also with 0.
 * {@code admittedPercent} is admitted ÷ total, rounded to a whole number (29 enquiries, 4 admitted → 14).
 * {@code byVillage} is the biggest village first.
 */
public record EnquirySummaryResponse(long total, Map<EnquiryStatus, Long> byStatus, long overdue, long admitted,
		int admittedPercent, List<VillageCount> byVillage) {

	/** Example: {@code Jakhal, 9}. */
	public record VillageCount(String village, long count) {

	}

}

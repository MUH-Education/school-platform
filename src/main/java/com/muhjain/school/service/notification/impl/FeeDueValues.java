package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.FamilyDues;
import com.muhjain.school.util.MoneyText;
import com.muhjain.school.util.RecipientText;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The fee reminder boxes, written once for the resolver (draft time) and the guard (send time), so both always give
 * the same text. Example: {"Amount due": "₹8,500", "Months": "सितंबर और अक्टूबर", "Months late": "1",
 * "Late fee": "₹100"}.
 */
public final class FeeDueValues {

	private FeeDueValues() {
	}

	public static Map<String, String> of(FamilyDues dues) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("Amount due", MoneyText.of(dues.amount()));
		values.put("Months", RecipientText.hindiMonths(dues.dueMonths()));
		values.put("Months late", String.valueOf(dues.monthsLate()));
		values.put("Late fee", MoneyText.of(dues.lateFee()));
		return values;
	}

	/** The audience's fee filters, on the family total. */
	public static boolean passes(FamilyDues dues, AudienceFilter filter) {
		if (filter.minMonthsLate() != null && dues.monthsLate() < filter.minMonthsLate()) {
			return false;
		}
		return filter.minAmount() == null || dues.amount().compareTo(filter.minAmount()) >= 0;
	}

}

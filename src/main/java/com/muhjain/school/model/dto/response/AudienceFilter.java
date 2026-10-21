package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.FeeStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * The filters of an {@link Audience}. Only the resolvers read them; a filter a group does not use is ignored.
 * Every field may be empty (null or an empty list = no filter).
 *
 * @param classes CURRENT_PARENTS, ROUTE: children in these classes. Example: ["10", "12"]
 * @param routeIds CURRENT_PARENTS, ROUTE, ROUTE_CREW: these routes
 * @param villages CURRENT_PARENTS: children from these villages
 * @param feeStatus CURRENT_PARENTS: families with this fee status
 * @param minMonthsLate FAMILIES_WITH_DUES: at least this many LATE months
 * @param minAmount FAMILIES_WITH_DUES: at least this much due (the live dues, not a stored number)
 * @param skipRemindedWithinDays FAMILIES_WITH_DUES: no second reminder within these days (default 7)
 * @param skipPromised FAMILIES_WITH_DUES: skip families who promised a date that has not passed
 * @param familyIds ONE_FAMILY: guardian ids
 * @param staffIds ONE_STAFF: staff ids
 */
public record AudienceFilter(List<String> classes, List<Long> routeIds, List<String> villages, FeeStatus feeStatus,
		Integer minMonthsLate, BigDecimal minAmount, Integer skipRemindedWithinDays, Boolean skipPromised,
		List<Long> familyIds, List<Long> staffIds) {

	public static final AudienceFilter NONE = new AudienceFilter(null, null, null, null, null, null, null, null, null,
			null);

	public AudienceFilter {
		classes = (classes == null) ? List.of() : List.copyOf(classes);
		routeIds = (routeIds == null) ? List.of() : List.copyOf(routeIds);
		villages = (villages == null) ? List.of() : List.copyOf(villages);
		familyIds = (familyIds == null) ? List.of() : List.copyOf(familyIds);
		staffIds = (staffIds == null) ? List.of() : List.copyOf(staffIds);
	}

}

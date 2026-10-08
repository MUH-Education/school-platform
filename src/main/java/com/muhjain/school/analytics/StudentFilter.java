package com.muhjain.school.analytics;

import java.util.List;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.fee.FeeStatus;
import com.muhjain.school.student.BusFilter;
import com.muhjain.school.student.ClassNames;
import com.muhjain.school.student.FatherOccupation;

/**
 * The one filter of all seven Analytics URLs (rule 1 of phase 8). Every part is optional.
 * Example: {@code village=Jakhal, feeStatus=DELAYED} → only children from Jakhal who are late.
 * <ul>
 * <li>{@code sessionId} — null means the current school year.</li>
 * <li>{@code classNames} — one class or a group, already checked and in the stored spelling. "1-5" → [1, 2, 3, 4, 5].
 * Null means every class.</li>
 * <li>{@code village} — not case sensitive.</li>
 * <li>{@code routeId} — children on this route today. {@code bus=NO} — children with no bus today.</li>
 * </ul>
 * The ids are only checked here for their shape. {@link AnalyticsBase} looks them up in the database.
 */
public record StudentFilter(Long sessionId, List<String> classNames, String village, Long routeId, BusFilter bus,
		FatherOccupation occupation, FeeStatus feeStatus) {

	/**
	 * @param className what the client typed, example "3" or "1-5"
	 * @throws ApiException 400 VALIDATION for a class that is not a class or group, or for {@code routeId} together
	 * with {@code bus=NO} (a child cannot be on a route and on no bus)
	 */
	public static StudentFilter of(Long sessionId, String className, String village, Long routeId, BusFilter bus,
			FatherOccupation occupation, FeeStatus feeStatus) {
		List<String> classes = null;
		if (className != null && !className.isBlank()) {
			classes = ClassNames.expand(className)
				.orElseThrow(() -> ApiException.validation("className",
						"must be Nursery, LKG, UKG, 1 to 12, or a group like 1-5"));
		}
		if (routeId != null && bus == BusFilter.NO) {
			throw ApiException.validation("bus", "cannot be NO together with routeId");
		}
		String cleanVillage = (village == null || village.isBlank()) ? null : village.strip();
		return new StudentFilter(sessionId, classes, cleanVillage, routeId, bus, occupation, feeStatus);
	}

	/** No filter at all: the current school year and every active child. */
	public static StudentFilter none() {
		return new StudentFilter(null, null, null, null, null, null, null);
	}

}

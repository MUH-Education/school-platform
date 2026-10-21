package com.muhjain.school.model.dto.response;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.enums.BusFilter;
import com.muhjain.school.model.enums.FatherOccupation;
import com.muhjain.school.model.enums.FeeStatus;
import com.muhjain.school.service.analytics.AnalyticsBase;
import com.muhjain.school.util.ClassNames;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

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
public record AnalyticsStudentFilter(
		@Schema(description = "School year. Empty means the current one.", example = "1") Long sessionId,
		@Schema(description = "One class or a group written from-to. Nursery, LKG, UKG or 1 to 12.",
				example = "1-5") List<String> classNames,
		@Schema(description = "Village, capital letters do not matter.", example = "Jakhal") String village,
		@Schema(description = "Children on this route today.", example = "4") Long routeId,
		@Schema(description = "NO means children with no bus today. Not together with routeId.",
				example = "NO") BusFilter bus,
		@Schema(description = "Father's occupation.", example = "FARMER_SMALL") FatherOccupation occupation,
		@Schema(description = "Fee status as of today.", example = "DELAYED") FeeStatus feeStatus) {

	/**
	 * @param className what the client typed, example "3" or "1-5"
	 * @throws ApiException 400 VALIDATION for a class that is not a class or group, or for {@code routeId} together
	 * with {@code bus=NO} (a child cannot be on a route and on no bus)
	 */
	public static AnalyticsStudentFilter of(Long sessionId, String className, String village, Long routeId, BusFilter bus,
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
		return new AnalyticsStudentFilter(sessionId, classes, cleanVillage, routeId, bus, occupation, feeStatus);
	}

	/** No filter at all: the current school year and every active child. */
	public static AnalyticsStudentFilter none() {
		return new AnalyticsStudentFilter(null, null, null, null, null, null, null);
	}

}

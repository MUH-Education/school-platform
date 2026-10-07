package com.muhjain.school.staff;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Change the driver, attendant or helper of a vehicle (rules 6 to 8).
 * <ul>
 * <li>Permanent: {@code temporary} false or left out, no {@code toDate}.
 * Example: from 1 Nov, Surender drives Van 4 instead of Jagdish.</li>
 * <li>Temporary: {@code temporary} true, {@code toDate} needed.
 * Example: Jagdish is on leave 12 to 16 Oct, Surender drives those days.</li>
 * </ul>
 * Example: {@code { "duty": "DRIVER", "staffId": 21, "fromDate": "2026-10-12", "toDate": "2026-10-16",
 * "temporary": true, "reason": "ON_LEAVE" }}
 */
public record ChangeAssignmentRequest(@NotNull Duty duty, @NotNull @Positive Long staffId,
		@NotNull LocalDate fromDate, LocalDate toDate, Boolean temporary, ChangeReason reason) {

}

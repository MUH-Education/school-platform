package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.LeftReason;
import com.muhjain.school.model.enums.StudentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * A child leaves school, or comes back after a wrong entry.
 * <ul>
 * <li>Leaves: {@code { "status": "LEFT", "leftOn": "2026-10-20", "leftReason": "MOVING_TOWN" }}.
 * {@code leftOn} is optional and means today. It cannot be in the future or before the joining day.
 * {@code leftReason} is required. The open bus row ends on that day.</li>
 * <li>Back: {@code { "status": "ACTIVE" }}. The bus is not restored, start it again from the student page.</li>
 * </ul>
 */
public record UpdateStatusRequest(@NotNull StudentStatus status, LocalDate leftOn, LeftReason leftReason,
		@Size(max = 300) String leftNote) {

}

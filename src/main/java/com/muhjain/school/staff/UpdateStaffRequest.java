package com.muhjain.school.staff;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Change a person. Send the whole person, like the edit form shows it.
 * {@code active: false} turns the person off (same rule as DELETE). {@code active: true} turns them on again.
 */
public record UpdateStaffRequest(@NotBlank @Size(max = 120) String name, @NotBlank String phone,
		@NotNull StaffType staffType, @Size(max = 30) String licenceNo, LocalDate licenceValidTill,
		@NotNull Boolean active) {

}

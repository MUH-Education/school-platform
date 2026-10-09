package com.muhjain.school.staff;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Change a person. Send the whole person, like the edit form shows it. A field that is left out is cleared,
 * so the form must always send back what it loaded.
 * {@code active: false} turns the person off (same rule as DELETE). {@code active: true} turns them on again.
 * The salary is never part of this: it has its own URL and its own permission.
 */
public record UpdateStaffRequest(@NotBlank @Size(max = 120) String name, @NotBlank String phone,
		@NotNull StaffType staffType, @Size(max = 30) String licenceNo, LocalDate licenceValidTill,
		@NotNull Boolean active, @Valid EmployeeDetails details, @Valid Teaching teaching) {

}

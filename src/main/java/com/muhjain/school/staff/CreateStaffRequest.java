package com.muhjain.school.staff;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Add a person. A DRIVER also needs {@code licenceNo} and {@code licenceValidTill}. For others they are ignored.
 * Example: {@code { "name": "Jagdish", "phone": "98123 40010", "staffType": "DRIVER",
 * "licenceNo": "HR2620110012345", "licenceValidTill": "2029-03-31" }}
 */
public record CreateStaffRequest(@NotBlank @Size(max = 120) String name, @NotBlank String phone,
		@NotNull StaffType staffType, @Size(max = 30) String licenceNo, LocalDate licenceValidTill) {

}

package com.muhjain.school.staff;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Add a person. A DRIVER also needs {@code licenceNo} and {@code licenceValidTill}. For others they are ignored.
 * {@code details} is the office file and may be left out. {@code teaching} is only for a TEACHER; sending it
 * for anyone else is 400 {@code NOT_A_TEACHER}.
 * Example: {@code { "name": "Jagdish", "phone": "98123 40010", "staffType": "DRIVER",
 * "licenceNo": "HR2620110012345", "licenceValidTill": "2029-03-31" }}
 */
public record CreateStaffRequest(@NotBlank @Size(max = 120) String name, @NotBlank String phone,
		@NotNull StaffType staffType, @Size(max = 30) String licenceNo, LocalDate licenceValidTill,
		@Valid EmployeeDetails details, @Valid Teaching teaching) {

}

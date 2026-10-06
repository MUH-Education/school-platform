package com.muhjain.school.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Add a user. Phone and role are enough.
 * Example: {@code { "phone": "98123 40002", "role": "OFFICE_ADMIN" }}
 *
 * @param staffId required for ATTENDANT, not allowed for other roles
 */
public record CreateUserRequest(@NotBlank String phone, @NotNull Role role, @Size(max = 120) String name,
		@Positive Long staffId) {

}

package com.muhjain.school.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Change a user. Send the whole user, like the edit form shows it.
 * Example: {@code { "phone": "98123 40002", "name": "Neelam", "role": "OFFICE_ADMIN", "active": false }}
 */
public record UpdateUserRequest(@NotBlank String phone, @NotNull Role role, @Size(max = 120) String name,
		@Positive Long staffId, @NotNull Boolean active) {

}

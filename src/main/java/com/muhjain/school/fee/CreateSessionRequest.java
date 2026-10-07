package com.muhjain.school.fee;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Add a school year. {@code current: true} makes it the current one and the old one stops being current.
 * Example: {@code {"name":"2027-28","startsOn":"2027-04-01","endsOn":"2028-03-31","current":false}}
 */
public record CreateSessionRequest(@NotBlank String name, @NotNull LocalDate startsOn, @NotNull LocalDate endsOn,
		Boolean current) {

}

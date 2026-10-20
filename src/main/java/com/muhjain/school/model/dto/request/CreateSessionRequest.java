package com.muhjain.school.model.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Add a school year. There is no "make current" flag: a session is current exactly when today falls
 * between its dates. Example: {@code {"name":"2027-28","startsOn":"2027-04-01","endsOn":"2028-03-31"}}
 */
public record CreateSessionRequest(@NotBlank String name, @NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {

}

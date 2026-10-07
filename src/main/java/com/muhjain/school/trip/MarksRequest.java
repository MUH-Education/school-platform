package com.muhjain.school.trip;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /api/v1/trips/marks}: one or many taps. At most 500 in one call. */
public record MarksRequest(@NotEmpty @Size(max = 500) List<@Valid @NotNull MarkRequest> marks) {

}

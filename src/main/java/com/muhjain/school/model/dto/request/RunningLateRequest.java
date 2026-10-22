package com.muhjain.school.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The attendant's "running late" button. Example: {@code {"minutes": 20}}. */
public record RunningLateRequest(@NotNull @Min(5) @Max(180) Integer minutes) {

}

package com.muhjain.school.model.dto.request;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Change a route. Send the whole route: name, vehicle (null = no vehicle), {@code active}, and the two school due
 * times (null = not set). {@code active: false} turns the route off (same rule as DELETE). {@code active: true}
 * turns it on again.
 */
public record UpdateRouteRequest(@NotBlank @Size(max = 80) String name, @Positive Long vehicleId,
		@NotNull Boolean active, LocalTime schoolDueMorning, LocalTime schoolDueEvening) {

}

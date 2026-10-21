package com.muhjain.school.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * Change a route. Send the whole route: name, vehicle (null = no vehicle), {@code active}, and the two school
 * due times. The due times are optional: left out, the route keeps whatever it already had (there is no "not
 * set" state any more, so there is always something to keep). {@code active: false} turns the route off (same
 * rule as DELETE). {@code active: true} turns it on again.
 */
public record UpdateRouteRequest(@NotBlank @Size(max = 80) String name, @Positive Long vehicleId,
		@NotNull Boolean active, LocalTime schoolArrivalTime, LocalTime schoolLeaveTime) {

}

package com.muhjain.school.model.dto.request;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.muhjain.school.model.entity.Route;

/**
 * Add a route. The vehicle is optional and can be set later. {@code schoolArrivalTime}/{@code schoolLeaveTime}
 * are optional too: left out, they default to 08:10 and 14:40 like the database does. Example:
 * {@code { "name": "Route 4", "vehicleId": 4, "schoolArrivalTime": "08:10", "schoolLeaveTime": "14:35" }}
 */
public record CreateRouteRequest(@NotBlank @Size(max = 80) String name, @Positive Long vehicleId,
		LocalTime schoolArrivalTime, LocalTime schoolLeaveTime) {

}

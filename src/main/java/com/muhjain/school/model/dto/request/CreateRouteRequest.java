package com.muhjain.school.model.dto.request;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.muhjain.school.model.entity.Route;

/**
 * Add a route. The vehicle is optional and can be set later. {@code schoolDueMorning}/{@code schoolDueEvening} are
 * optional too (null until the office knows the time). Example: {@code { "name": "Route 4", "vehicleId": 4,
 * "schoolDueMorning": "08:10", "schoolDueEvening": "14:35" }}
 */
public record CreateRouteRequest(@NotBlank @Size(max = 80) String name, @Positive Long vehicleId,
		LocalTime schoolDueMorning, LocalTime schoolDueEvening) {

}

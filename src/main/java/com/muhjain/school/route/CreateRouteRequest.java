package com.muhjain.school.route;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Add a route. The vehicle is optional and can be set later. Example: {@code { "name": "Route 4", "vehicleId": 4 }} */
public record CreateRouteRequest(@NotBlank @Size(max = 80) String name, @Positive Long vehicleId) {

}

package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.entity.Route;

/** A route in short. Example: {@code { "id": 4, "name": "Route 4" }}. Shown on the vehicle screens. */
public record RouteRef(Long id, String name) {

}

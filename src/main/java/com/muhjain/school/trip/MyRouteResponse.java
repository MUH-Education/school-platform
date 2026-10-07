package com.muhjain.school.trip;

import java.time.LocalDate;

/**
 * The attendant's route today and the progress of the four jobs. If the attendant works on no route today,
 * {@code route} is null and {@code jobs} is null (the app shows "no bus today").
 * Example: Route 4, Van 4, 19 children, boarding 11 done 1 absent 7 to do.
 */
public record MyRouteResponse(LocalDate date, Route route, Integer total, JobsResponse jobs) {

	/** Example: {@code { "id": 4, "name": "Route 4", "vehicle": "Van 4" }} */
	public record Route(Long id, String name, String vehicle) {

	}

}

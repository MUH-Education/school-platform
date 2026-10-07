package com.muhjain.school.trip;

import java.time.LocalDate;
import java.util.List;

/** "One bus (Route 4)": the route's status and every child with the four events. */
public record RouteDetailResponse(LocalDate date, RouteStatusResponse route, List<RouteChildStatus> children) {

}

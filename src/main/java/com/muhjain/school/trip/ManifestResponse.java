package com.muhjain.school.trip;

import java.time.LocalDate;
import java.util.List;

/** The list the attendant works from: stops in morning order, children under each stop, taps so far. */
public record ManifestResponse(Long routeId, String routeName, LocalDate date, List<ManifestStop> stops) {

}

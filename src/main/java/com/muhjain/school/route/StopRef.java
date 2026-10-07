package com.muhjain.school.route;

/** A stop in short, for other features. Example: {@code StopRef(44, 9, "Model Town")} is a stop of route 9. */
public record StopRef(Long id, Long routeId, String name) {

}

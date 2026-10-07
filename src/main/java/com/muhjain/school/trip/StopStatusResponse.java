package com.muhjain.school.trip;

/**
 * A stop on Bus status. {@code due} and {@code tappedAt} are "HH:mm" in school time. {@code tappedAt} is null until
 * someone is tapped there.
 * Example: {@code { "name": "Jakhal", "due": "07:40", "tappedAt": "07:42", "state": "DONE" }}.
 */
public record StopStatusResponse(Long stopId, String name, String due, String tappedAt, StopState state) {

}

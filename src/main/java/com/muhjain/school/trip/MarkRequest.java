package com.muhjain.school.trip;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * One tap sent by the phone. Example: {@code studentId 118, BOARDED_MORNING, DONE, 2026-10-07,
 * 2026-10-07T07:42:10+05:30}. There is no routeId: the server finds the route of the child (never trust the client).
 */
public record MarkRequest(@NotNull Long studentId, @NotNull EventType eventType, @NotNull Outcome outcome,
		@NotNull LocalDate serviceDate, @NotNull Instant occurredAt) {

}

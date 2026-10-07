package com.muhjain.school.trip;

import java.time.OffsetDateTime;

/** One tap in an answer. Example: {@code {"outcome":"DONE","occurredAt":"2026-10-07T07:42:10+05:30"}}. */
public record TapResponse(Outcome outcome, OffsetDateTime occurredAt) {

}

package com.muhjain.school.model.dto.request;

import java.time.OffsetDateTime;

/**
 * Send now or later: exactly one of the two. Examples: {@code {"sendNow": true}},
 * {@code {"scheduledAt": "2026-10-10T10:00:00+05:30"}}.
 */
public record SendNotificationRequest(Boolean sendNow, OffsetDateTime scheduledAt) {
}

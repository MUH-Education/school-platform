package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.AttentionKind;

/**
 * One problem on {@code GET /bus-status/attention}. {@code title} and {@code message} are plain English
 * sentences, already written by the server; the web app shows them as-is.
 * Example: {@code { "routeId": 3, "kind": "NO_TAPS", "title": "Route 3 has no taps yet",
 * "message": "The first stop, Pirthala, was due at 07:15. That is 33 minutes ago. Attendant: Mahender." }}
 */
public record AttentionEntry(Long routeId, AttentionKind kind, String title, String message) {

}

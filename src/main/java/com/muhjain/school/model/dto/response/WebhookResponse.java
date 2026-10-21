package com.muhjain.school.model.dto.response;

/**
 * The answer to a provider callback. {@code changed} = false when it was a repeat or about something we do not know:
 * still 200, so the provider stops sending it. Example: {@code {"received": true, "changed": false}}.
 */
public record WebhookResponse(boolean received, boolean changed) {
}

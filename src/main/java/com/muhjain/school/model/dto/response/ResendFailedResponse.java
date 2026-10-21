package com.muhjain.school.model.dto.response;

/** "Resend failed": how many failed messages went back to the queue. Example: {@code {"requeued": 4}}. */
public record ResendFailedResponse(int requeued) {
}

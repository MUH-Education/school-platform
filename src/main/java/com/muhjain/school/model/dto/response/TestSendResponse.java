package com.muhjain.school.model.dto.response;

/**
 * The test copy that was queued for the caller's own phone, with the example values filled in.
 * Example: {@code {"phone":"+91XXXXXX0001","body":"नमस्ते Rajesh Punia, PTM 10 अक्टूबर को है।"}}.
 */
public record TestSendResponse(String phone, String body) {
}

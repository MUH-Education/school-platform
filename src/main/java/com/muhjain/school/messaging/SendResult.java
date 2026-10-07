package com.muhjain.school.messaging;

/**
 * What a sender answers. {@code providerRef} is the provider's id for the message (may be null).
 * {@code testOnly} is true when nothing left the server (the log sender); the row then becomes TEST_ONLY.
 */
public record SendResult(String providerRef, boolean testOnly) {

}

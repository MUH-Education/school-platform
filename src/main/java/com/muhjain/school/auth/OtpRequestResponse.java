package com.muhjain.school.auth;

/**
 * The same answer for every phone, known or not.
 * Example: {@code { "message": "If this number is registered, a code has been sent.", "expiresInSeconds": 300,
 * "resendAfterSeconds": 60 }}
 */
public record OtpRequestResponse(String message, long expiresInSeconds, long resendAfterSeconds) {

}

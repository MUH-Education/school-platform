package com.muhjain.school.student;

/** The bytes of a photo and its type, ready to send. Example: 84 KB of {@code image/jpeg}. */
public record PhotoContent(byte[] bytes, String contentType) {

}

package com.muhjain.school.model.enums;

/**
 * The file sent with a notification. Limits: IMAGE 5 MB, VIDEO 16 MB (MP4), DOCUMENT 5 MB (PDF), AUDIO 16 MB.
 * Not called {@code MediaType}: Spring already has {@code org.springframework.http.MediaType}.
 */
public enum MediaFileType {

	IMAGE, VIDEO, DOCUMENT, AUDIO

}

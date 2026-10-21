package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.MediaFileType;

/**
 * A saved notification file. The React app keeps {@code mediaKey} and sends it with the template or the
 * notification. Example: {@code {"mediaKey":"notifications/9b1e....mp4","mediaType":"VIDEO","bytes":8123456,
 * "width":null,"height":null,"seconds":45}}.
 *
 * @param width pixels, pictures only
 * @param height pixels, pictures only
 * @param seconds length, MP4 video and M4A audio only (null when it cannot be read)
 */
public record NotificationMediaResponse(String mediaKey, MediaFileType mediaType, long bytes, Integer width,
		Integer height, Integer seconds) {
}

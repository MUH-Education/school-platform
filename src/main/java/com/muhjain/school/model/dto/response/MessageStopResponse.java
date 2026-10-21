package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.StopReason;

import java.time.OffsetDateTime;

/**
 * One family on the stop list (no more marketing or greetings). The phone is masked: the list is to see who, not to
 * call. Example: guardian 210, "Rajesh Punia", +91XXXXXX0001, WROTE_STOP, after notification 17, 7 Oct 18:29.
 */
public record MessageStopResponse(Long guardianId, String name, String phone, StopReason reason, Long notificationId,
		OffsetDateTime createdAt) {
}

package com.muhjain.school.messaging;

import java.time.OffsetDateTime;

import com.muhjain.school.trip.EventType;

/**
 * One line of the Messages screen. The phone is masked: {@code +91XXXXXX4321}.
 * Example: {@code 7 Oct 07:42, Aryan Jain, +91XXXXXX0001, BOARDED_MORNING, "Aryan सुबह की बस ...", SENT}.
 */
public record MessageResponse(Long id, OffsetDateTime createdAt, MessagePurpose purpose, Long studentId,
		String studentName, String phone, EventType eventType, String body, MessageStatus status, int attempts,
		String error, OffsetDateTime sentAt) {

}

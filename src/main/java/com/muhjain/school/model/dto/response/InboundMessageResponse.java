package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.ButtonAction;
import com.muhjain.school.model.enums.RecipientType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One reply that came in to the school number, for the replies list. The phone is shown in full: the office may need
 * to call back ("Asked for a call"). {@code fromType} / {@code fromId} / {@code fromName}: the parent, employee or
 * enquiry with that phone (in that order), all null when the phone is unknown.
 * Example: 12, +919811100004, GUARDIAN 213 "Anil Sharma", "शुक्रवार तक जमा कर देंगे", handled by Neelam,
 * promised until 23 Oct 2026.
 */
public record InboundMessageResponse(Long id, String phone, RecipientType fromType, Long fromId, String fromName,
		String text, ButtonAction buttonAction, Long inReplyToOutboxId, OffsetDateTime receivedAt, boolean handled,
		Long handledBy, String handledByName, String note, LocalDate promisedUntil, Long enquiryId) {
}

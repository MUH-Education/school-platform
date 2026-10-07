package com.muhjain.school.enquiry;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One call or visit. Example: "Called, will visit on Sunday", next 12 Oct, by Neelam. */
public record FollowUpResponse(Long id, String note, LocalDate nextActionOn, OffsetDateTime createdAt,
		String createdBy) {

}

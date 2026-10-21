package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.EnquiryFollowType;
import com.muhjain.school.model.enums.EnquiryNextType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One call or visit. Example: CALL, "Called, will visit on Sunday", next VISIT 12 Oct, by Neelam. */
public record FollowUpResponse(Long id, LocalDate happenedOn, EnquiryFollowType followType, String note,
		EnquiryNextType nextType, LocalDate nextOn, OffsetDateTime createdAt, String createdBy) {

}

package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.EnquiryLostReason;
import com.muhjain.school.model.enums.EnquiryStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Move an enquiry to another stage. {@code lostReason} is needed for LOST; {@code lostNote} is optional free
 * text kept alongside it. ADMITTED cannot be set here.
 * Example: {@code {"status":"LOST","lostReason":"CHOSE_OTHER_SCHOOL","lostNote":"Joined a school near home"}}.
 */
public record StatusRequest(@NotNull EnquiryStatus status, EnquiryLostReason lostReason,
		@Size(max = 300) String lostNote) {

}

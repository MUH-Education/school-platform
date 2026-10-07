package com.muhjain.school.enquiry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Move an enquiry to another stage. {@code lostReason} is needed for LOST. ADMITTED cannot be set here.
 * Example: {@code {"status":"LOST","lostReason":"Joined a school near home"}}.
 */
public record StatusRequest(@NotNull EnquiryStatus status, @Size(max = 200) String lostReason) {

}

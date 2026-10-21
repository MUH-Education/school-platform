package com.muhjain.school.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Mark a reply as done. All optional:
 * <ul>
 * <li>{@code note}: what the office did. Example: "Called back, will visit on Sunday".</li>
 * <li>{@code promisedUntil}: "will pay by Friday" — no fee reminder to this phone until that day (today to 90 days
 * ahead).</li>
 * <li>{@code makeEnquiry} true + {@code enquiry}: an enquiry with this reply's phone (also needs ENQUIRIES_EDIT).</li>
 * </ul>
 * Example: {@code {"note":"Will pay on Friday","promisedUntil":"2026-10-09"}}.
 */
public record HandleInboundRequest(@Size(max = 300) String note, LocalDate promisedUntil, Boolean makeEnquiry,
		@Valid EnquiryFromReplyRequest enquiry) {
}

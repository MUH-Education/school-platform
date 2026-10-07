package com.muhjain.school.enquiry;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A call or a visit. {@code nextActionOn} is the next day to call, it cannot be in the past.
 * Example: {@code {"note":"Called. Will visit on Sunday.","nextActionOn":"2026-10-12"}}.
 */
public record FollowUpRequest(@NotBlank @Size(max = 1000) String note, LocalDate nextActionOn) {

}

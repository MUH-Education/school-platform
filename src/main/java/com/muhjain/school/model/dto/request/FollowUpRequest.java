package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.EnquiryFollowType;
import com.muhjain.school.model.enums.EnquiryNextType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * A call or a visit. {@code happenedOn} is optional and means today. {@code nextOn} is the next day to call, it
 * cannot be in the past.
 * Example: {@code {"followType":"CALL","note":"Called. Will visit on Sunday.","nextType":"VISIT",
 * "nextOn":"2026-10-12"}}.
 */
public record FollowUpRequest(LocalDate happenedOn, @NotNull EnquiryFollowType followType,
		@NotBlank @Size(max = 1000) String note, EnquiryNextType nextType, LocalDate nextOn) {

}

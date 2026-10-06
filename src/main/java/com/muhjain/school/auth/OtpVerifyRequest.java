package com.muhjain.school.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Example: {@code { "phone": "98123 40002", "otp": "482913" }} */
public record OtpVerifyRequest(@NotBlank String phone,
		@NotBlank @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String otp) {

}

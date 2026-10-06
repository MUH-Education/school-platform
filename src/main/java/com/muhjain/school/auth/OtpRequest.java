package com.muhjain.school.auth;

import jakarta.validation.constraints.NotBlank;

/** Example: {@code { "phone": "98123 40002" }} */
public record OtpRequest(@NotBlank String phone) {

}

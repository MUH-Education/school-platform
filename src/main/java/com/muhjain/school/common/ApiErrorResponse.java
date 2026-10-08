package com.muhjain.school.common;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The JSON body of every error.
 * Example: {@code { "error": "OTP_INVALID", "message": "The code is wrong or too old.", "fields": null }}
 *
 * @param error   stable code the React app can switch on
 * @param message text for a person
 * @param fields  field name to problem, only for validation errors (HTTP 400), otherwise null
 */
@Schema(name = "ApiError", description = "The body of every error answer.",
		example = "{\"error\": \"OTP_INVALID\", \"message\": \"The code is wrong or too old.\", \"fields\": null}")
public record ApiErrorResponse(
		@Schema(description = "Stable code. The React app switches on it.", example = "OTP_INVALID") String error,
		@Schema(description = "Text for a person.", example = "The code is wrong or too old.") String message,
		@Schema(description = "Field name to problem, for example seats: must be greater than 0. Only for validation "
				+ "errors (400), otherwise null.", nullable = true) Map<String, String> fields) {

	public static ApiErrorResponse of(String error, String message) {
		return new ApiErrorResponse(error, message, null);
	}

}

package com.muhjain.school.common;

import java.util.Map;

/**
 * The JSON body of every error.
 * Example: {@code { "error": "OTP_INVALID", "message": "The code is wrong or too old.", "fields": null }}
 *
 * @param error   stable code the React app can switch on
 * @param message text for a person
 * @param fields  field name to problem, only for validation errors (HTTP 400), otherwise null
 */
public record ApiErrorResponse(String error, String message, Map<String, String> fields) {

	public static ApiErrorResponse of(String error, String message) {
		return new ApiErrorResponse(error, message, null);
	}

}

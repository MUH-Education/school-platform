package com.muhjain.school.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Throw this from any service to stop and answer the client with a clear error.
 * Example: {@code throw new ApiException(HttpStatus.CONFLICT, "VEHICLE_IN_USE", "This vehicle runs Route 4.")}
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final Map<String, String> fields;
	private final Long retryAfterSeconds;

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, null, null);
	}

	private ApiException(HttpStatus status, String code, String message, Map<String, String> fields,
			Long retryAfterSeconds) {
		super(message);
		this.status = status;
		this.code = code;
		this.fields = fields;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	/**
	 * 400 VALIDATION from a service rule. Example: {@code validation("staffId", "is required for an attendant")}
	 * → {@code fields: {"staffId": "is required for an attendant"}}.
	 */
	public static ApiException validation(String field, String problem) {
		return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Some fields are wrong. Please check them.",
				Map.of(field, problem), null);
	}

	/**
	 * 409 that also tells the client which row is in the way. Example:
	 * {@code conflict("ENQUIRY_EXISTS", "...", Map.of("enquiryId", "12"))}.
	 */
	public static ApiException conflict(String code, String message, Map<String, String> fields) {
		return new ApiException(HttpStatus.CONFLICT, code, message, fields, null);
	}

	/** 429 with a {@code Retry-After: 42} header. Example: "wait 42 seconds before asking for a new code". */
	public static ApiException tooManyRequests(String code, String message, long retryAfterSeconds) {
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message, null, Math.max(1, retryAfterSeconds));
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	/** For 400 VALIDATION, and for a 409 that names the row in the way. Otherwise null. */
	public Map<String, String> getFields() {
		return fields;
	}

	/** Only for some 429 answers, otherwise null. */
	public Long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}

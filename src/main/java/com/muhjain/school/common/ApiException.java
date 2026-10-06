package com.muhjain.school.common;

import org.springframework.http.HttpStatus;

/**
 * Throw this from any service to stop and answer the client with a clear error.
 * Example: {@code throw new ApiException(HttpStatus.CONFLICT, "VEHICLE_IN_USE", "This vehicle runs Route 4.")}
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

}

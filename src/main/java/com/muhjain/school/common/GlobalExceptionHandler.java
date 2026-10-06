package com.muhjain.school.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns every error from a controller into one JSON shape, {@link ApiErrorResponse}.
 * Example: {@code { "error": "VEHICLE_IN_USE", "message": "This vehicle runs Route 4.", "fields": null }}
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final String VALIDATION = "VALIDATION";

	private static final String VALIDATION_MESSAGE = "Some fields are wrong. Please check them.";

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
		ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.getStatus());
		if (ex.getRetryAfterSeconds() != null) {
			response.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
		}
		return response.body(new ApiErrorResponse(ex.getCode(), ex.getMessage(), ex.getFields()));
	}

	// A @Valid request body broke a rule. Example: seats = 0 → fields {"seats": "must be greater than 0"}
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fields.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
			fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage());
		}
		return validation(VALIDATION_MESSAGE, fields);
	}

	// A rule on a method parameter broke. Example: @RequestParam @Min(0) int page with page = -1
	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidParameters(HandlerMethodValidationException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		for (ParameterValidationResult result : ex.getParameterValidationResults()) {
			String parameter = result.getMethodParameter().getParameterName();
			for (MessageSourceResolvable error : result.getResolvableErrors()) {
				String field = (error instanceof FieldError fieldError) ? fieldError.getField() : parameter;
				fields.putIfAbsent(field, error.getDefaultMessage());
			}
		}
		return validation(VALIDATION_MESSAGE, fields);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
		return validation("The request body could not be read. Check the JSON.", null);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return validation(VALIDATION_MESSAGE, Map.of(ex.getName(), "has the wrong format"));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
		return validation(VALIDATION_MESSAGE, Map.of(ex.getParameterName(), "is required"));
	}

	/**
	 * Everything else. Spring's own web errors keep their HTTP status, and the code is the status name
	 * (405 → METHOD_NOT_ALLOWED). Any other error is a bug: 500 INTERNAL_SERVER_ERROR, details only in the log.
	 */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleOther(Exception ex) throws Exception {
		if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
			// Spring Security answers these itself with 401 or 403.
			throw ex;
		}
		if (ex instanceof ErrorResponse springError) {
			HttpStatusCode status = springError.getStatusCode();
			HttpStatus known = HttpStatus.resolve(status.value());
			String code = (known != null) ? known.name() : String.valueOf(status.value());
			String message = (known != null) ? known.getReasonPhrase() : "The request failed.";
			if (status.is5xxServerError()) {
				log.error("Request failed with {}", status, ex);
			}
			return ResponseEntity.status(status)
				.headers(springError.getHeaders())
				.body(ApiErrorResponse.of(code, message));
		}
		log.error("Unexpected error", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.name(),
					"Something went wrong. Please try again."));
	}

	private static ResponseEntity<ApiErrorResponse> validation(String message, Map<String, String> fields) {
		return ResponseEntity.badRequest().body(new ApiErrorResponse(VALIDATION, message, fields));
	}

}

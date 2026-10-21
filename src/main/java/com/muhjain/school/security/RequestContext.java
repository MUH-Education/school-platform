package com.muhjain.school.security;

import com.muhjain.school.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Holds {@link RequestContextData} for the thread handling the current request.
 * {@link UserJwtConverter} sets it once a token is checked; {@link RequestContextCleanupFilter}
 * clears it when the request ends, even after an error — Tomcat reuses threads across requests,
 * so without that, one person's request could leak into the next one on the same thread.
 */
public final class RequestContext {

	private static final ThreadLocal<RequestContextData> CURRENT = new ThreadLocal<>();

	private RequestContext() {
	}

	public static void set(RequestContextData data) {
		CURRENT.set(data);
	}

	/** @throws ApiException 401 UNAUTHENTICATED if nobody is logged in */
	public static RequestContextData get() {
		RequestContextData data = CURRENT.get();
		if (data == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please log in first.");
		}
		return data;
	}

	public static void clear() {
		CURRENT.remove();
	}

}

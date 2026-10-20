package com.muhjain.school.security;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.enums.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who is logged in, taken from the checked token (and the session it points at). Never from the
 * request body. Example: Neelam's token → {@code currentUser.id()} is 2.
 */
@Component
public class CurrentUser {

	/** @throws ApiException 401 UNAUTHENTICATED if nobody is logged in */
	public Long id() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication instanceof AnonymousAuthenticationToken
				|| !authentication.isAuthenticated()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please log in first.");
		}
		return Long.valueOf(authentication.getName());
	}

	/** The role of the logged-in user, straight from the session (no extra database call). */
	public Role role() {
		return RequestContext.get().role();
	}

	/** The attendant's staff row, or null for anyone who is not one. */
	public Long staffId() {
		return RequestContext.get().staffId();
	}

	/** The id of the session this request is using. Needed to log out this device only. */
	public String sessionId() {
		return RequestContext.get().sessionId();
	}

}

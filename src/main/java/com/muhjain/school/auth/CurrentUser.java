package com.muhjain.school.auth;

import com.muhjain.school.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The id of the logged-in user, taken from the checked token. Never from the request body.
 * Example: Neelam's token → {@code currentUser.id()} is 2.
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

}

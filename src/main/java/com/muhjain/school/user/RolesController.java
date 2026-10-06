package com.muhjain.school.user;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/roles}: the fixed role and permission table. Needs USERS_MANAGE. */
@RestController
public class RolesController {

	private final UserService userService;

	public RolesController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/api/v1/roles")
	@PreAuthorize("hasAuthority('USERS_MANAGE')")
	public RolesResponse roles() {
		return userService.roles();
	}

}

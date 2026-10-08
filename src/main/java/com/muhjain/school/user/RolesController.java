package com.muhjain.school.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/roles}: the fixed role and permission table. Needs USERS_MANAGE. */
@Tag(name = "Users")
@RestController
public class RolesController {

	private final UserService userService;

	public RolesController(UserService userService) {
		this.userService = userService;
	}

	@Operation(summary = "List roles and their permissions")
	@GetMapping("/api/v1/roles")
	@PreAuthorize("hasAuthority('USERS_MANAGE')")
	public RolesResponse roles() {
		return userService.roles();
	}

}

package com.muhjain.school.user;

import java.util.List;

import com.muhjain.school.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Users and roles screen. Every URL needs USERS_MANAGE (only the owner has it).
 * Users are never deleted, only turned off ({@code "active": false}).
 */
@Tag(name = "Users")
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('USERS_MANAGE')")
public class UserController {

	private final UserService userService;

	private final CurrentUser currentUser;

	public UserController(UserService userService, CurrentUser currentUser) {
		this.userService = userService;
		this.currentUser = currentUser;
	}

	@Operation(summary = "List users")
	@GetMapping
	public List<UserResponse> list() {
		return userService.list();
	}

	@Operation(summary = "Add a user")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
		return userService.create(request, currentUser.id());
	}

	@Operation(summary = "Change a user")
	@PutMapping("/{id}")
	public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
		return userService.update(id, request, currentUser.id());
	}

}

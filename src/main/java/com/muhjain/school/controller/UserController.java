package com.muhjain.school.controller;

import com.muhjain.school.model.dto.request.CreateUserRequest;
import com.muhjain.school.model.dto.request.UpdateUserRequest;
import com.muhjain.school.model.dto.response.UserResponse;
import com.muhjain.school.security.CurrentUser;
import com.muhjain.school.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

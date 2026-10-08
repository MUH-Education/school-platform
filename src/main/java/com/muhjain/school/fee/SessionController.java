package com.muhjain.school.fee;

import java.util.List;

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
 * School years and the standard fee of each class.
 * <ul>
 * <li>{@code GET /api/v1/sessions} — any login</li>
 * <li>{@code POST /api/v1/sessions} — SETTINGS_EDIT</li>
 * <li>{@code GET /api/v1/sessions/{id}/class-fees} — FEES_VIEW</li>
 * <li>{@code PUT /api/v1/sessions/{id}/class-fees} — SETTINGS_EDIT</li>
 * </ul>
 */
@Tag(name = "Fees")
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

	private final SessionService sessionService;

	private final ClassFeeService classFeeService;

	public SessionController(SessionService sessionService, ClassFeeService classFeeService) {
		this.sessionService = sessionService;
		this.classFeeService = classFeeService;
	}

	@Operation(summary = "List school years")
	@GetMapping
	public List<SessionResponse> list() {
		return sessionService.list();
	}

	@Operation(summary = "Add a school year")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('SETTINGS_EDIT')")
	public SessionResponse create(@Valid @RequestBody CreateSessionRequest request) {
		return sessionService.create(request);
	}

	@Operation(summary = "Standard fee of each class")
	@GetMapping("/{id}/class-fees")
	@PreAuthorize("hasAuthority('FEES_VIEW')")
	public List<ClassFeeItem> classFees(@PathVariable Long id) {
		return classFeeService.list(id);
	}

	@Operation(summary = "Save the standard fee of each class")
	@PutMapping("/{id}/class-fees")
	@PreAuthorize("hasAuthority('SETTINGS_EDIT')")
	public List<ClassFeeItem> saveClassFees(@PathVariable Long id, @Valid @RequestBody ClassFeesRequest request) {
		return classFeeService.replace(id, request);
	}

}

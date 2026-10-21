package com.muhjain.school.controller;

import com.muhjain.school.model.dto.request.UpdateSettingsRequest;
import com.muhjain.school.model.dto.response.SettingResponse;
import com.muhjain.school.security.CurrentUser;
import com.muhjain.school.service.setting.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * School-wide numbers.
 * <ul>
 * <li>{@code GET /api/v1/settings} — any login</li>
 * <li>{@code PUT /api/v1/settings} — SETTINGS_EDIT</li>
 * </ul>
 */
@Tag(name = "Settings")
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

	private final SettingService settingService;

	private final CurrentUser currentUser;

	public SettingsController(SettingService settingService, CurrentUser currentUser) {
		this.settingService = settingService;
		this.currentUser = currentUser;
	}

	@Operation(summary = "List settings")
	@GetMapping
	public List<SettingResponse> list() {
		return settingService.list();
	}

	@Operation(summary = "Change settings")
	@PutMapping
	@PreAuthorize("hasAuthority('SETTINGS_EDIT')")
	public List<SettingResponse> update(@Valid @RequestBody UpdateSettingsRequest request) {
		return settingService.update(request, currentUser.id());
	}

}

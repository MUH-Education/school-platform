package com.muhjain.school.setting;

import java.util.List;

import com.muhjain.school.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * School-wide numbers.
 * <ul>
 * <li>{@code GET /api/v1/settings} — any login</li>
 * <li>{@code PUT /api/v1/settings} — SETTINGS_EDIT</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

	private final SettingService settingService;

	private final CurrentUser currentUser;

	public SettingsController(SettingService settingService, CurrentUser currentUser) {
		this.settingService = settingService;
		this.currentUser = currentUser;
	}

	@GetMapping
	public List<SettingResponse> list() {
		return settingService.list();
	}

	@PutMapping
	@PreAuthorize("hasAuthority('SETTINGS_EDIT')")
	public List<SettingResponse> update(@Valid @RequestBody UpdateSettingsRequest request) {
		return settingService.update(request, currentUser.id());
	}

}

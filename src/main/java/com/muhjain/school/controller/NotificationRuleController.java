package com.muhjain.school.controller;

import com.muhjain.school.model.dto.request.NotificationRuleRequest;
import com.muhjain.school.model.dto.response.NotificationRuleResponse;
import com.muhjain.school.model.dto.response.RuleRunResult;
import com.muhjain.school.security.CurrentUser;
import com.muhjain.school.service.notification.NotificationRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The Settings screen's "Automatic messages" table (Phase 12). See docs/06-api.md, "Automatic messages".
 * Seeing the table needs MARKETING_VIEW; changing a rule or running it by hand needs SETTINGS_EDIT (owner).
 */
@RestController
@RequestMapping("/api/v1/notification-rules")
@Tag(name = "Notifications")
public class NotificationRuleController {

	private final NotificationRuleService ruleService;

	private final CurrentUser currentUser;

	public NotificationRuleController(NotificationRuleService ruleService, CurrentUser currentUser) {
		this.ruleService = ruleService;
		this.currentUser = currentUser;
	}

	@Operation(summary = "Every automatic message, with its last run and last 30 days' counts")
	@GetMapping
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public List<NotificationRuleResponse> list() {
		return ruleService.list();
	}

	/** Only what is sent changes. Example: {@code {"enabled": false}} turns the rule off. */
	@Operation(summary = "Change an automatic message (on/off, template, audience, time, festival dates)")
	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('SETTINGS_EDIT')")
	public NotificationRuleResponse update(@PathVariable Long id, @Valid @RequestBody NotificationRuleRequest request) {
		return ruleService.update(id, request, currentUser.id());
	}

	/** Fires the rule once now, even when it is off. Answers CREATED + notification id, or NOBODY. */
	@Operation(summary = "Run an automatic message once, now")
	@PostMapping("/{id}/run-now")
	@PreAuthorize("hasAuthority('SETTINGS_EDIT')")
	public RuleRunResult runNow(@PathVariable Long id) {
		return ruleService.runNow(id, currentUser.id());
	}

}

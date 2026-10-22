package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.NotificationKind;

import java.time.Instant;
import java.util.Map;

/**
 * What a rule asks {@code NotificationService.createFromRule} to make. Example: rule 1 "Fee reminder 10th, 10 Oct 2026",
 * FEE_REMINDER, template 41, FAMILIES_WITH_DUES, no fixed values, now, made by user 1 (the owner who turned it on).
 *
 * @param scheduledAt null = send now (SENDING); a time = SCHEDULED for then (WELCOME: tomorrow 9:00)
 * @param createdBy the user who last turned the rule on or changed it
 */
public record RuleNotification(Long ruleId, String name, NotificationKind kind, Long templateId, Audience audience,
		Map<String, String> fixedValues, Instant scheduledAt, Long createdBy) {

	public RuleNotification {
		fixedValues = (fixedValues == null) ? Map.of() : Map.copyOf(fixedValues);
	}

}

package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.RuleRunStatus;

/**
 * The answer of one {@code RuleService.fire}. Example: CREATED, notification 57. {@code notificationId} is null
 * unless CREATED.
 */
public record RuleRunResult(RuleRunStatus status, Long notificationId) {

	public static RuleRunResult of(RuleRunStatus status) {
		return new RuleRunResult(status, null);
	}

}

package com.muhjain.school.service.notification;

import com.muhjain.school.model.dto.response.RefineInput;
import com.muhjain.school.model.dto.response.RefineResult;

/**
 * Suggests a better text for a notification (docs/10-notifications.md section 7). Only a suggestion: nothing is
 * saved and nothing is sent. {@code impl.ClaudeTextRefiner} when {@code app.ai.api-key} is set,
 * {@code impl.LogTextRefiner} otherwise. {@link NotificationRefineService} checks every answer.
 */
public interface TextRefiner {

	/** @throws com.muhjain.school.exception.ApiException when there is no suggestion (not set up, declined, down) */
	RefineResult refine(RefineInput input);

}

package com.muhjain.school.service.notification.impl;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.response.RefineInput;
import com.muhjain.school.model.dto.response.RefineResult;
import com.muhjain.school.service.notification.TextRefiner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Used while {@code app.ai.api-key} is empty. In {@code test} it answers the text unchanged, with the note
 * "AI is not set up", so the screen can be tried. Anywhere else it answers 409 REFINE_NOT_CONFIGURED.
 */
@Component
@ConditionalOnExpression("'${app.ai.api-key:}'.trim().isEmpty()")
public class LogTextRefiner implements TextRefiner {

	private final Environment environment;

	public LogTextRefiner(Environment environment) {
		this.environment = environment;
	}

	@Override
	public RefineResult refine(RefineInput input) {
		if (!environment.matchesProfiles("test")) {
			throw new ApiException(HttpStatus.CONFLICT, "REFINE_NOT_CONFIGURED",
					"The AI helper is not set up on this server (no key).");
		}
		return new RefineResult(input.text(), "AI is not set up");
	}

}

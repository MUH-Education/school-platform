package com.muhjain.school.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code app.ai.*}: the AI "Refine" helper of the notification screen (docs/10-notifications.md section 7).
 * Off while {@code apiKey} is empty. The key comes only from the environment ({@code APP_AI_API_KEY}); never log it.
 *
 * @param model the Claude model. Example: {@code claude-opus-5-5}
 * @param effort how hard the model thinks: {@code low} is enough to reword a short message
 * @param timeout the whole call; the office waits for it. Example: 20s
 * @param maxTokens the answer's limit, thinking included (a message is at most 1024 letters)
 */
@ConfigurationProperties("app.ai")
public record AiProperties(String apiKey, String model, String effort, Duration timeout, String baseUrl,
		Integer maxTokens) {

	public AiProperties {
		apiKey = (apiKey == null) ? "" : apiKey.trim();
		model = (model == null || model.isBlank()) ? "claude-opus-5-5" : model.trim();
		effort = (effort == null || effort.isBlank()) ? "low" : effort.trim();
		timeout = (timeout == null) ? Duration.ofSeconds(20) : timeout;
		baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.anthropic.com" : baseUrl.trim();
		maxTokens = (maxTokens == null) ? 4000 : maxTokens;
	}

	public boolean configured() {
		return !apiKey.isEmpty();
	}

	/** Never print the key, also not by accident in a log of this record. */
	@Override
	public String toString() {
		return "AiProperties[model=" + model + ", effort=" + effort + ", timeout=" + timeout + ", configured="
				+ configured() + "]";
	}

}

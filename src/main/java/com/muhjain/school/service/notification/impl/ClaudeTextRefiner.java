package com.muhjain.school.service.notification.impl;

import com.muhjain.school.config.AiProperties;
import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.response.RefineInput;
import com.muhjain.school.model.dto.response.RefineResult;
import com.muhjain.school.service.notification.TextRefiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Claude Messages API, called with Spring's {@link RestClient} (no new library, docs/08-decisions.md):
 * {@code POST /v1/messages} with the system prompt from {@code prompts/refine.txt} and one user message (action,
 * instruction, language, text). Low effort (a short rewrite); a server-side fallback re-runs a request the model
 * declines on Anthropic's recommended model, so the office rarely sees a refusal.
 * <p>
 * Never logs the text or the key. Used when {@code app.ai.api-key} is set.
 */
@Component
@ConditionalOnExpression("!'${app.ai.api-key:}'.trim().isEmpty()")
public class ClaudeTextRefiner implements TextRefiner {

	private static final Logger log = LoggerFactory.getLogger(ClaudeTextRefiner.class);

	static final String API_VERSION = "2023-06-01";

	/** Opt-in for {@code "fallbacks": "default"} (re-run a declined request on another model). */
	static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

	private final AiProperties properties;

	private final RestClient http;

	private final String systemPrompt;

	@Autowired
	public ClaudeTextRefiner(AiProperties properties) {
		this(properties, client(properties));
	}

	/** For tests: a {@link RestClient} bound to a mock server. */
	public ClaudeTextRefiner(AiProperties properties, RestClient http) {
		this.properties = properties;
		this.http = http;
		this.systemPrompt = loadPrompt();
	}

	private static RestClient client(AiProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(properties.timeout());
		return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
	}

	private static String loadPrompt() {
		try {
			return new ClassPathResource("prompts/refine.txt").getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("prompts/refine.txt is missing", ex);
		}
	}

	@Override
	public RefineResult refine(RefineInput input) {
		JsonNode answer;
		try {
			answer = http.post()
				.uri("/v1/messages")
				.contentType(MediaType.APPLICATION_JSON)
				.header("x-api-key", properties.apiKey())
				.header("anthropic-version", API_VERSION)
				.header("anthropic-beta", FALLBACK_BETA)
				.body(requestBody(input))
				.retrieve()
				.body(JsonNode.class);
		}
		catch (RestClientResponseException ex) {
			// 429, 500 and 529 are passing; 400, 401, 403 and 404 are our set-up (key, model). Either way the office
			// can write the text itself. The status and the error type go to the log, never the key or the text.
			log.warn("AI refine failed: HTTP {}", ex.getStatusCode().value());
			throw unavailable();
		}
		catch (ResourceAccessException ex) {
			log.warn("AI refine did not answer in {}", properties.timeout());
			throw unavailable();
		}
		if (answer == null) {
			throw unavailable();
		}
		String stopReason = answer.path("stop_reason").asString("");
		if ("refusal".equals(stopReason)) {
			throw new ApiException(HttpStatus.CONFLICT, "REFINE_REFUSED",
					"The AI did not want to change this text. Please change it yourself.");
		}
		StringBuilder text = new StringBuilder();
		JsonNode content = answer.path("content");
		for (int i = 0; i < content.size(); i++) {
			JsonNode block = content.get(i);
			if ("text".equals(block.path("type").asString(""))) {
				text.append(block.path("text").asString(""));
			}
		}
		if ("max_tokens".equals(stopReason) || text.isEmpty()) {
			throw new ApiException(HttpStatus.CONFLICT, "REFINE_FAILED", "The AI gave no usable text. Try again.");
		}
		return new RefineResult(text.toString(), null);
	}

	private Map<String, Object> requestBody(RefineInput input) {
		StringBuilder user = new StringBuilder();
		user.append("Action: ").append(input.action().name()).append('\n');
		if (input.instruction() != null && !input.instruction().isBlank()) {
			user.append("Instruction: ").append(input.instruction().strip()).append('\n');
		}
		user.append("Language: ").append("en".equals(input.language()) ? "English" : "Hindi").append('\n');
		user.append("Text:\n").append(input.text());

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.model());
		body.put("max_tokens", properties.maxTokens());
		body.put("system", systemPrompt);
		body.put("messages", List.of(Map.of("role", "user", "content", user.toString())));
		body.put("output_config", Map.of("effort", properties.effort()));
		body.put("fallbacks", "default");
		return body;
	}

	private static ApiException unavailable() {
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REFINE_UNAVAILABLE",
				"The AI helper did not answer. Try again in a minute, or change the text yourself.");
	}

}

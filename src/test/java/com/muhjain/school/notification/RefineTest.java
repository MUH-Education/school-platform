package com.muhjain.school.notification;

import com.muhjain.school.config.AiProperties;
import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.RefineTextRequest;
import com.muhjain.school.model.dto.response.RefineInput;
import com.muhjain.school.model.dto.response.RefineResult;
import com.muhjain.school.model.enums.RefineAction;
import com.muhjain.school.service.notification.NotificationRefineService;
import com.muhjain.school.service.notification.TextRefiner;
import com.muhjain.school.service.notification.impl.ClaudeTextRefiner;
import com.muhjain.school.service.notification.impl.LogTextRefiner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Task 11.12, the AI "Refine" helper, without a real AI call: the Claude Messages API is a mock HTTP server.
 * docs/10-notifications.md section 7.
 */
class RefineTest {

	private static final String TEXT = "नमस्ते {Parent name}, {Child name} की PTM इस शनिवार सुबह 10 बजे स्कूल में होगी। "
			+ "कृपया समय पर आएँ। — MUH Jain School";

	private static final AiProperties AI = new AiProperties("test-key-not-real", null, null, Duration.ofSeconds(20),
			null, null);

	private MockRestServiceServer server;

	private NotificationRefineService service;

	@BeforeEach
	void mockApi() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.com");
		server = MockRestServiceServer.bindTo(builder).build();
		service = new NotificationRefineService(new ClaudeTextRefiner(AI, builder.build()));
	}

	private static String answer(String stopReason, String text) {
		return "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"stop_reason\":\"" + stopReason
				+ "\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"\"},{\"type\":\"text\",\"text\":\"" + text
				+ "\"}]}";
	}

	private static RefineTextRequest shorter() {
		return new RefineTextRequest(TEXT, RefineAction.SHORTER, null, null);
	}

	@Test
	void theRequestHasTheRightShape() {
		server.expect(requestTo("https://api.anthropic.com/v1/messages"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("x-api-key", "test-key-not-real"))
			.andExpect(header("anthropic-version", "2023-06-01"))
			.andExpect(header("anthropic-beta", "server-side-fallback-2026-07-01"))
			.andExpect(jsonPath("$.model").value("claude-opus-5-5"))
			.andExpect(jsonPath("$.output_config.effort").value("low"))
			.andExpect(jsonPath("$.fallbacks").value("default"))
			.andExpect(jsonPath("$.system", containsString("Keep every {box} exactly as written")))
			.andExpect(jsonPath("$.messages[0].role").value("user"))
			.andExpect(jsonPath("$.messages[0].content", containsString("Action: SHORTER")))
			.andExpect(jsonPath("$.messages[0].content", containsString("Language: Hindi")))
			.andExpect(jsonPath("$.messages[0].content", containsString(TEXT)))
			// No thinking setting: the model decides (low effort keeps it short).
			.andExpect(jsonPath("$.thinking").doesNotExist())
			.andRespond(withSuccess(answer("end_turn", "{Parent name} जी, {Child name} की PTM शनिवार 10 बजे। समय पर आएँ।"),
					MediaType.APPLICATION_JSON));
		service.refine(shorter());
		server.verify();
	}

	@Test
	void refineKeepsPlaceholdersOrFails() {
		// The answer lost {Child name} ("आपके बच्चे" instead): dropped, never shown as a suggestion.
		server.expect(requestTo("https://api.anthropic.com/v1/messages"))
			.andRespond(withSuccess(answer("end_turn", "{Parent name} जी, आपके बच्चे की PTM शनिवार 10 बजे।"),
					MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> service.refine(shorter())).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus().value()).isEqualTo(409);
			assertThat(ex.getCode()).isEqualTo("REFINE_LOST_PLACEHOLDER");
			assertThat(ex.getMessage()).contains("{Child name}");
		});
		server.reset();

		String good = "{Parent name} जी, {Child name} की PTM शनिवार 10 बजे। समय पर आएँ।";
		server.expect(requestTo("https://api.anthropic.com/v1/messages"))
			.andRespond(withSuccess(answer("end_turn", "  " + good + "\\n"), MediaType.APPLICATION_JSON));
		RefineResult result = service.refine(shorter());
		assertThat(result.text()).isEqualTo(good);   // trimmed
		int shorterBy = TEXT.codePointCount(0, TEXT.length()) - good.codePointCount(0, good.length());
		assertThat(result.note()).isEqualTo(shorterBy + " letters shorter");   // letters, not bytes
	}

	@Test
	void aDeclinedRequestIsAClearAnswer() {
		server.expect(requestTo("https://api.anthropic.com/v1/messages"))
			.andRespond(withSuccess("{\"stop_reason\":\"refusal\",\"content\":[]}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> service.refine(shorter()))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("REFINE_REFUSED"));
	}

	@Test
	void aCutAnswerIsNotASuggestion() {
		server.expect(requestTo("https://api.anthropic.com/v1/messages"))
			.andRespond(withSuccess(answer("max_tokens", "{Parent name} जी, {Child name} की"),
					MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> service.refine(shorter()))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("REFINE_FAILED"));
	}

	@Test
	void providerDownIs503AndNeverShowsTheKey() {
		server.expect(requestTo("https://api.anthropic.com/v1/messages"))
			.andRespond(withStatus(HttpStatusCode.valueOf(529)).body("{\"type\":\"error\",\"error\":{\"type\":"
					+ "\"overloaded_error\",\"message\":\"Overloaded\"}}").contentType(MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> service.refine(shorter())).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus().value()).isEqualTo(503);
			assertThat(ex.getCode()).isEqualTo("REFINE_UNAVAILABLE");
			assertThat(ex.getMessage()).doesNotContain("test-key-not-real");
		});
		assertThat(AI.toString()).doesNotContain("test-key-not-real");
	}

	@Test
	void refineOffWithoutKey() {
		RefineInput input = new RefineInput(TEXT, RefineAction.SHORTER, null, "hi", java.util.Set.of());
		// Any profile but test: a clear 409.
		assertThatThrownBy(() -> new LogTextRefiner(new MockEnvironment()).refine(input))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getStatus().value()).isEqualTo(409);
				assertThat(ex.getCode()).isEqualTo("REFINE_NOT_CONFIGURED");
			});
		// In test: the text back, unchanged.
		MockEnvironment test = new MockEnvironment();
		test.setActiveProfiles("test");
		assertThat(new LogTextRefiner(test).refine(input)).isEqualTo(new RefineResult(TEXT, "AI is not set up"));
	}

	@Test
	void customNeedsAnInstructionAndLongAnswersAreCut() {
		AtomicReference<RefineInput> seen = new AtomicReference<>();
		TextRefiner fake = input -> {
			seen.set(input);
			return new RefineResult("{Parent name} " + "अ".repeat(2000), null);
		};
		NotificationRefineService withFake = new NotificationRefineService(fake);
		assertThatThrownBy(() -> withFake.refine(new RefineTextRequest("{Parent name} जी", RefineAction.CUSTOM, " ",
				null))).isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("VALIDATION"));

		RefineResult cut = withFake.refine(new RefineTextRequest("{Parent name} जी", RefineAction.CUSTOM,
				"principal की तरफ़ से लिखो", "hi"));
		assertThat(cut.text().codePointCount(0, cut.text().length())).isEqualTo(1024);
		assertThat(seen.get().placeholders()).containsExactly("Parent name");
		assertThat(seen.get().language()).isEqualTo("hi");
		assertThat(cut.note()).endsWith("letters longer");
	}

}

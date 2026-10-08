package com.muhjain.school.common;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 8.10: with the prod setting (docs and UI off) the docs are not served, to nobody. The same two properties are
 * set in the prod part of application.yml, and {@code ApplicationYamlDocsTest} checks that file.
 */
@TestPropertySource(properties = { "springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false" })
class OpenApiOffInProdTest extends AbstractIntegrationTest {

	@Test
	void withoutATokenTheDocsAndTheUiAreNeverTheirPage() throws Exception {
		for (String url : new String[] { "/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui/index.html",
				"/swagger-ui.html", "/swagger-ui/swagger-ui.css" }) {
			var response = mockMvc.perform(get(url)).andReturn().getResponse();
			assertThat(response.getStatus()).as(url).isIn(401, 404);
			assertThat(response.getContentAsString()).as(url).doesNotContain("openapi").doesNotContain("swagger-ui");
		}
	}

	@Test
	void evenTheOwnerWithATokenGetsNoDocs() throws Exception {
		String owner = tokenFor(addUser("+919812340001", Role.OWNER));
		for (String url : new String[] { "/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html" }) {
			mockMvc.perform(get(url).header("Authorization", bearer(owner))).andExpect(status().isNotFound());
		}
	}

	@Test
	void theApiItselfStillWorks() throws Exception {
		String owner = tokenFor(addUser("+919812340001", Role.OWNER));
		mockMvc.perform(get("/api/v1/analytics/summary").header("Authorization", bearer(owner)))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/analytics/summary")).andExpect(status().isUnauthorized());
	}

}

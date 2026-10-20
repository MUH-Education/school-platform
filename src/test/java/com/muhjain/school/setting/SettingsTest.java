package com.muhjain.school.setting;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettingsTest extends AbstractIntegrationTest {

	@AfterEach
	void putStartValuesBack() {
		jdbc.update("update app_setting set value = '11', updated_by = null where key = 'transport.months_operated'");
		jdbc.update("update app_setting set value = 'MUH Jain Global School', updated_by = null where key = 'school.name'");
	}

	@Test
	void anyLoginCanReadSettings() throws Exception {
		String token = tokenFor(addUser("+919812340005", Role.ATTENDANT));

		mockMvc.perform(get("/api/v1/settings").header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(11))
			.andExpect(jsonPath("$[?(@.key == 'transport.months_operated')].value").value("11"));
	}

	@Test
	void ownerChangesAValueAndItIsAudited() throws Exception {
		AppUser owner = addUser("+919812340001", Role.OWNER);

		update(tokenFor(owner),
				"{\"values\":{\"transport.months_operated\":\" 10 \",\"school.name\":\"MUH Jain Global School\"}}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.key == 'transport.months_operated')].value").value("10"))
			.andExpect(jsonPath("$[?(@.key == 'transport.months_operated')].updatedBy").value(owner.getId().intValue()));

		assertThat(jdbc.queryForList("select summary from audit_log where entity_type = 'SETTING'", String.class))
			.containsExactly("transport.months_operated changed from 11 to 10");
	}

	@Test
	void badValueOrUnknownKeySavesNothing() throws Exception {
		String token = tokenFor(addUser("+919812340001", Role.OWNER));

		update(token, "{\"values\":{\"transport.months_operated\":\"10\",\"transport.collection_pct\":\"120\"}}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$['fields']['transport.collection_pct']").value("must be at most 100"));
		update(token, "{\"values\":{\"bus.colour\":\"yellow\"}}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$['fields']['bus.colour']").value("is not a setting"));
		update(token, "{\"values\":{\"fees.defaulted_after_days\":\"ten\"}}").andExpect(status().isBadRequest());
		update(token, "{\"values\":{\"school.office_phone\":\"123\"}}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$['fields']['school.office_phone']").exists());

		assertThat(jdbc.queryForObject("select value from app_setting where key = 'transport.months_operated'",
				String.class))
			.isEqualTo("11");
	}

	@Test
	void optionalTextSettingsMayBeLeftBlank() throws Exception {
		String token = tokenFor(addUser("+919812340001", Role.OWNER));

		update(token, "{\"values\":{\"school.principal_name\":\"\"}}").andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.key == 'school.principal_name')].value").value(""));
	}

	@Test
	void officeAdminCannotChangeSettings() throws Exception {
		String token = tokenFor(addUser("+919812340002", Role.OFFICE_ADMIN));

		update(token, "{\"values\":{\"transport.months_operated\":\"10\"}}").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("FORBIDDEN"));
	}

	private ResultActions update(String token, String json) throws Exception {
		return mockMvc.perform(put("/api/v1/settings").header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

}

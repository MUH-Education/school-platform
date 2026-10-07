package com.muhjain.school.setting;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Role;
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
		jdbc.update("update app_setting set value = '8800', updated_by = null where key = 'transport.bus_fee_per_year'");
		jdbc.update("update app_setting set value = 'MUH Jain School', updated_by = null where key = 'school.name'");
	}

	@Test
	void anyLoginCanReadSettings() throws Exception {
		String token = tokenFor(addUser("+919812340005", Role.ATTENDANT));

		mockMvc.perform(get("/api/v1/settings").header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(7))
			.andExpect(jsonPath("$[?(@.key == 'transport.bus_fee_per_year')].value").value("8800"));
	}

	@Test
	void ownerChangesAValueAndItIsAudited() throws Exception {
		AppUser owner = addUser("+919812340001", Role.OWNER);

		update(tokenFor(owner), "{\"values\":{\"transport.bus_fee_per_year\":\" 9000 \",\"school.name\":\"MUH Jain School\"}}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.key == 'transport.bus_fee_per_year')].value").value("9000"))
			.andExpect(jsonPath("$[?(@.key == 'transport.bus_fee_per_year')].updatedBy").value(owner.getId().intValue()));

		assertThat(jdbc.queryForList("select summary from audit_log where entity_type = 'SETTING'", String.class))
			.containsExactly("transport.bus_fee_per_year changed from 8800 to 9000");
	}

	@Test
	void badValueOrUnknownKeySavesNothing() throws Exception {
		String token = tokenFor(addUser("+919812340001", Role.OWNER));

		update(token, "{\"values\":{\"transport.bus_fee_per_year\":\"9000\",\"transport.collection_pct\":\"120\"}}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$['fields']['transport.collection_pct']").value("must be at most 100"));
		update(token, "{\"values\":{\"bus.colour\":\"yellow\"}}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$['fields']['bus.colour']").value("is not a setting"));
		update(token, "{\"values\":{\"fees.grace_days\":\"ten\"}}").andExpect(status().isBadRequest());

		assertThat(jdbc.queryForObject("select value from app_setting where key = 'transport.bus_fee_per_year'",
				String.class))
			.isEqualTo("8800");
	}

	@Test
	void officeAdminCannotChangeSettings() throws Exception {
		String token = tokenFor(addUser("+919812340002", Role.OFFICE_ADMIN));

		update(token, "{\"values\":{\"transport.bus_fee_per_year\":\"9000\"}}").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("FORBIDDEN"));
	}

	private ResultActions update(String token, String json) throws Exception {
		return mockMvc.perform(put("/api/v1/settings").header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

}

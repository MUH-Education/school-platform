package com.muhjain.school.dev;

import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Task 7.13: the dev data has class fees and a fee plan for every active child, with all three statuses. */
@ActiveProfiles({ "test", "dev" })
class DevDataFeesTest extends AbstractIntegrationTest {

	@Autowired
	private DevDataLoader loader;

	private String token;

	@BeforeEach
	void load() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		token = tokenFor(addUser("+919812340001", Role.OWNER));
		loader.run(null);
	}

	private int studentsWith(String feeStatus) throws Exception {
		String body = mockMvc.perform(get("/api/v1/students?size=100").header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		java.util.List<?> found = com.jayway.jsonpath.JsonPath.read(body, "$.items[?(@.feeStatus=='" + feeStatus + "')]");
		return found.size();
	}

	@Test
	void everyActiveChildHasAPlanAndClassFeesAreSet() {
		int active = jdbc.queryForObject("select count(*) from student where status = 'ACTIVE'", Integer.class);
		assertThat(jdbc.queryForObject("select count(*) from fee_plan", Integer.class)).isEqualTo(active);
		assertThat(jdbc.queryForObject("select count(*) from class_fee", Integer.class)).isEqualTo(15);
		assertThat(jdbc.queryForObject("select count(*) from fee_payment where amount <= 0", Integer.class)).isZero();
	}

	@Test
	void thereIsAMixOfOnTimeDelayedAndDefaulted() throws Exception {
		int onTime = studentsWith("ON_TIME");
		int delayed = studentsWith("DELAYED");
		int defaulted = studentsWith("DEFAULTED");

		assertThat(delayed).isPositive();
		assertThat(defaulted).isPositive();
		assertThat(onTime).isGreaterThan(delayed);
		assertThat(onTime + delayed + defaulted)
			.isEqualTo(jdbc.queryForObject("select count(*) from fee_plan", Integer.class));
	}

	@Test
	void runningAgainAddsNothing() {
		int plans = jdbc.queryForObject("select count(*) from fee_plan", Integer.class);
		int payments = jdbc.queryForObject("select count(*) from fee_payment", Integer.class);
		loader.run(null);
		assertThat(jdbc.queryForObject("select count(*) from fee_plan", Integer.class)).isEqualTo(plans);
		assertThat(jdbc.queryForObject("select count(*) from fee_payment", Integer.class)).isEqualTo(payments);
	}

}

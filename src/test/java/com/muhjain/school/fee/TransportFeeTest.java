package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rule 8 of phase 7: a bus that starts later adds BUS dues to the plan. */
class TransportFeeTest extends FeeTestBase {

	long ishaan;

	long[] route;

	@BeforeEach
	void addChildWithSchoolOnlyPlan() throws Exception {
		clock.setInstant(Instant.parse("2026-10-30T04:30:00Z"));
		ishaan = addStudent("A-2026-2", "2026-04-01");
		route = addBusRoute("Route 9");
		put(office, "/api/v1/students/" + ishaan + "/fee-plan", "{\"schoolFee\":30000,\"payFrequency\":\"QUARTERLY\"}")
			.andExpect(status().isOk());
	}

	private String startBus(String busFee) {
		return "{\"usesBus\":true,\"routeId\":" + route[0] + ",\"stopId\":" + route[1]
				+ ",\"fromDate\":\"2026-11-02\"" + ((busFee == null) ? "" : ",\"busFee\":" + busFee) + "}";
	}

	@Test
	void busStartedInNovemberAddsBusDues() throws Exception {
		put(office, "/api/v1/students/" + ishaan + "/transport", startBus("4000")).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select bus_fee from fee_plan", BigDecimal.class)).isEqualByComparingTo("4000");
		assertThat(jdbc.queryForList("select due_on::text || ' ' || amount from fee_due where fee_head = 'BUS' "
				+ "order by due_on", String.class))
			.containsExactly("2026-11-02 2000.00", "2027-01-01 2000.00");
		// The four school dues are not touched.
		assertThat(jdbc.queryForObject("select count(*) from fee_due where fee_head = 'SCHOOL'", Integer.class))
			.isEqualTo(4);
		get(office, "/api/v1/students/" + ishaan + "/fees").andExpect(jsonPath("$.dues", hasSize(6)))
			.andExpect(jsonPath("$.plan.busFee").value(4000.0));
		assertThat(jdbc.queryForList("select summary from audit_log where summary like 'Bus fee%'", String.class))
			.containsExactly("Bus fee ₹4,000 added to the fee plan from 2 Nov 2026. Bus fee is now ₹4,000.");
	}

	@Test
	void busWithoutAFeeOrWithoutAPlanChangesNothing() throws Exception {
		put(office, "/api/v1/students/" + ishaan + "/transport", startBus(null)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select bus_fee from fee_plan", BigDecimal.class)).isEqualByComparingTo("0");
		assertThat(jdbc.queryForObject("select count(*) from fee_due where fee_head = 'BUS'", Integer.class)).isZero();

		long noPlan = addStudent("A-2026-3", "2026-04-01");
		put(office, "/api/v1/students/" + noPlan + "/transport", startBus("4000")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from fee_plan", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from fee_due where fee_head = 'BUS'", Integer.class)).isZero();
	}

	@Test
	void savingThePlanAgainKeepsTheBusDuesFromTheBusStartDay() throws Exception {
		put(office, "/api/v1/students/" + ishaan + "/transport", startBus("4000")).andExpect(status().isOk());

		put(office, "/api/v1/students/" + ishaan + "/fee-plan",
				"{\"schoolFee\":30000,\"busFee\":4000,\"payFrequency\":\"QUARTERLY\"}").andExpect(status().isOk());

		assertThat(jdbc.queryForList("select due_on::text from fee_due where fee_head = 'BUS' order by due_on",
				String.class))
			.containsExactly("2026-11-02", "2027-01-01");
	}

}

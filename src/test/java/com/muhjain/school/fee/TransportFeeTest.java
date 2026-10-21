package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rule 8 of phase 7: a bus that starts later adds BUS amounts to the plan's fee_due rows. */
class TransportFeeTest extends FeeTestBase {

	long ishaan;

	long[] route;

	@BeforeEach
	void addChildWithSchoolOnlyPlan() throws Exception {
		clock.setInstant(Instant.parse("2026-10-30T04:30:00Z"));
		refreshTokens();
		ishaan = addStudent("A-2026-102", "2026-04-01");
		route = addBusRoute("Route 9");
		put(office, "/api/v1/students/" + ishaan + "/fee-plan", "{\"schoolFee\":2500,\"payFrequency\":\"QUARTERLY\"}")
			.andExpect(status().isOk());
	}

	private String startBus(String busFee) {
		return "{\"usesBus\":true,\"routeId\":" + route[0] + ",\"stopId\":" + route[1]
				+ ",\"fromDate\":\"2026-11-01\"" + ((busFee == null) ? "" : ",\"busFee\":" + busFee) + "}";
	}

	@Test
	void busStartedInNovemberAddsBusDues() throws Exception {
		put(office, "/api/v1/students/" + ishaan + "/transport", startBus("1000")).andExpect(status().isOk());

		assertThat(jdbc.queryForObject(
				"select bus_fee_per_month from transport_enrolment where student_id = ? and to_date is null",
				BigDecimal.class, ishaan)).isEqualByComparingTo("1000");
		assertThat(jdbc.queryForList("select month::text || ' ' || bus_amount from fee_due "
				+ "where student_id = ? and bus_amount > 0 order by month", String.class, ishaan))
			.containsExactly("2026-11-01 1000.00", "2026-12-01 1000.00", "2027-01-01 1000.00",
					"2027-02-01 1000.00", "2027-03-01 1000.00");
		// The school amount of every month is not touched by the bus starting.
		assertThat(jdbc.queryForObject(
				"select count(*) from fee_due where student_id = ? and school_amount <> 2500", Integer.class, ishaan))
			.isZero();
		get(office, "/api/v1/students/" + ishaan + "/fees").andExpect(jsonPath("$.dues", hasSize(12)))
			.andExpect(jsonPath("$.dues[7].total").value(3500.0));
	}

	@Test
	void busWithoutATypedFeeUsesTheSessionDefaultAndNoPlanChangesNothing() throws Exception {
		// A bus started with no busFee typed gets the session's standard bus fee instead (800 a month).
		put(office, "/api/v1/students/" + ishaan + "/transport", startBus(null)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject(
				"select bus_fee_per_month from transport_enrolment where student_id = ? and to_date is null",
				BigDecimal.class, ishaan)).isEqualByComparingTo("800");
		assertThat(jdbc.queryForObject(
				"select count(*) from fee_due where student_id = ? and bus_amount > 0", Integer.class, ishaan))
			.isGreaterThan(0);

		long noPlan = addStudent("A-2026-103", "2026-04-01");
		put(office, "/api/v1/students/" + noPlan + "/transport", startBus("4000")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from fee_plan", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from fee_due where student_id = ?", Integer.class, noPlan))
			.isZero();
	}

	@Test
	void savingThePlanAgainKeepsTheBusDuesFromTheBusStartDay() throws Exception {
		put(office, "/api/v1/students/" + ishaan + "/transport", startBus("1000")).andExpect(status().isOk());

		put(office, "/api/v1/students/" + ishaan + "/fee-plan", "{\"schoolFee\":2600,\"payFrequency\":\"QUARTERLY\"}")
			.andExpect(status().isOk());

		assertThat(jdbc.queryForList("select month::text from fee_due where student_id = ? and bus_amount > 0 "
				+ "order by month", String.class, ishaan))
			.containsExactly("2026-11-01", "2026-12-01", "2027-01-01", "2027-02-01", "2027-03-01");
	}

}

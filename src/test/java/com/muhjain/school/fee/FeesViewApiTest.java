package com.muhjain.school.fee;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rule 16 of phase 7: {@code GET /students/{id}/fees} and the "Done when" numbers. */
class FeesViewApiTest extends FeeTestBase {

	long aryan;

	@BeforeEach
	void addChildWithPlan() throws Exception {
		clock.setInstant(Instant.parse("2026-04-01T04:30:00Z"));
		aryan = addStudent("A-2026-1", "2026-04-01");
		put(office, "/api/v1/students/" + aryan + "/fee-plan", FeePlanApiTest.QUARTERLY).andExpect(status().isOk());
	}

	private void pay(String school, String bus) throws Exception {
		post(office, "/api/v1/students/" + aryan + "/payments", "{\"mode\":\"UPI\",\"lines\":[{\"feeHead\":\"SCHOOL\","
				+ "\"amount\":" + school + "},{\"feeHead\":\"BUS\",\"amount\":" + bus + "}]}")
			.andExpect(status().isCreated());
	}

	private org.springframework.test.web.servlet.ResultActions fees(String token) throws Exception {
		return get(token, "/api/v1/students/" + aryan + "/fees");
	}

	@Test
	void admittedOnFirstAprilWithFirstPaymentShowsNothingPendingAndNextDueOnFirstJuly() throws Exception {
		pay("7500", "2200");

		fees(office).andExpect(status().isOk())
			.andExpect(jsonPath("$.pendingNow").value(0.0))
			.andExpect(jsonPath("$.remainingThisYear").value(29100.0))
			.andExpect(jsonPath("$.nextDueOn").value("2026-07-01"))
			.andExpect(jsonPath("$.nextDueAmount").value(9700.0))
			.andExpect(jsonPath("$.status").value("ON_TIME"))
			.andExpect(jsonPath("$.plan.payFrequency").value("QUARTERLY"))
			.andExpect(jsonPath("$.dues", hasSize(8)))
			.andExpect(jsonPath("$.dues[0].covered").value(7500.0))
			.andExpect(jsonPath("$.dues[0].open").value(0.0))
			.andExpect(jsonPath("$.payments", hasSize(2)))
			.andExpect(jsonPath("$.heads[0].feeHead").value("SCHOOL"))
			.andExpect(jsonPath("$.heads[1].feeHead").value("BUS"));
	}

	@Test
	void on15AugustWithoutASecondPaymentStatusIsDelayed() throws Exception {
		pay("7500", "2200");
		clock.setInstant(Instant.parse("2026-08-15T04:30:00Z"));

		fees(office).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DELAYED"))
			.andExpect(jsonPath("$.pendingNow").value(9700.0))
			.andExpect(jsonPath("$.heads[0].daysLate").value(45))
			.andExpect(jsonPath("$.heads[0].oldestUnpaidDue").value("2026-07-01"))
			.andExpect(jsonPath("$.dues[2].overdue").value(true));
	}

	@Test
	void statusFollowsTheSettings() throws Exception {
		pay("7500", "2200");
		clock.setInstant(Instant.parse("2026-08-15T04:30:00Z"));
		// Grace 50 days: 45 days late is now on time.
		jdbc.update("update app_setting set value = '50' where key = 'fees.grace_days'");
		try {
			fees(office).andExpect(jsonPath("$.status").value("ON_TIME"));
		}
		finally {
			jdbc.update("update app_setting set value = '10' where key = 'fees.grace_days'");
		}
	}

	@Test
	void childWithoutAPlanGetsAnEmptyAnswerNotAnError() throws Exception {
		long ishaan = addStudent("A-2026-2", "2026-04-01");

		get(desk, "/api/v1/students/" + ishaan + "/fees").andExpect(status().isOk())
			.andExpect(jsonPath("$.plan").value(nullValue()))
			.andExpect(jsonPath("$.status").value(nullValue()))
			.andExpect(jsonPath("$.dues", hasSize(0)))
			.andExpect(jsonPath("$.pendingNow").value(0.0));
	}

	@Test
	void unknownChildIs404AndTransportInchargeIs403() throws Exception {
		get(office, "/api/v1/students/99999/fees").andExpect(status().isNotFound());
		fees(transport).andExpect(status().isForbidden());
	}

	@Test
	void aCorrectionMakesTheMoneyPendingAgain() throws Exception {
		pay("7500", "2200");
		post(owner, "/api/v1/students/" + aryan + "/payment-corrections",
				"{\"receiptNo\":\"R-2026-0001\",\"feeHead\":\"SCHOOL\",\"amount\":-500,\"note\":\"Paid 7000\"}")
			.andExpect(status().isCreated());

		fees(office).andExpect(jsonPath("$.pendingNow").value(500.0))
			.andExpect(jsonPath("$.remainingThisYear").value(29600.0))
			.andExpect(jsonPath("$.payments", hasSize(3)));
	}

}

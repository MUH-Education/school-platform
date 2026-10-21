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
		aryan = addStudent("A-2026-101", "2026-04-01");
		long[] route = addBusRoute("Route 1");
		addBusEnrolment(aryan, route, "800", "2026-04-01");
		put(office, "/api/v1/students/" + aryan + "/fee-plan", FeePlanApiTest.STANDARD_PLAN).andExpect(status().isOk());
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
		pay("2500", "800");

		fees(office).andExpect(status().isOk())
			.andExpect(jsonPath("$.pendingNow").value(0.0))
			.andExpect(jsonPath("$.remainingThisYear").value(35500.0))
			.andExpect(jsonPath("$.nextDueOn").value("2026-05-01"))
			.andExpect(jsonPath("$.nextDueAmount").value(3300.0))
			.andExpect(jsonPath("$.status").value("ON_TIME"))
			.andExpect(jsonPath("$.plan.payFrequency").value("QUARTERLY"))
			.andExpect(jsonPath("$.dues", hasSize(12)))
			.andExpect(jsonPath("$.dues[0].paid").value(3300.0))
			.andExpect(jsonPath("$.dues[0].open").value(0.0))
			.andExpect(jsonPath("$.payments", hasSize(2)))
			.andExpect(jsonPath("$.heads[0].feeHead").value("SCHOOL"))
			.andExpect(jsonPath("$.heads[1].feeHead").value("BUS"));
	}

	@Test
	void on20MayWithoutASecondPaymentStatusIsDelayed() throws Exception {
		pay("2500", "800");
		// April is paid. May's due (1 May, grace 10 May) is the oldest unpaid one; 20 May is 19 days
		// past it: past the grace day but inside the 60-day defaulted window.
		clock.setInstant(Instant.parse("2026-05-20T04:30:00Z"));
		refreshTokens();

		fees(office).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DELAYED"))
			.andExpect(jsonPath("$.pendingNow").value(3300.0))
			.andExpect(jsonPath("$.heads[0].daysLate").value(19))
			.andExpect(jsonPath("$.heads[0].oldestUnpaidDue").value("2026-05-01"))
			.andExpect(jsonPath("$.dues[1].late").value(true));
	}

	@Test
	void statusFollowsTheSettings() throws Exception {
		pay("2500", "800");
		clock.setInstant(Instant.parse("2026-05-20T04:30:00Z"));
		refreshTokens();
		// Grace until the end of the month: 19 days late in May is still inside it.
		jdbc.update("update session_fee_setting set grace_until = 31 where session_id = ?", currentSessionId());
		try {
			fees(office).andExpect(jsonPath("$.status").value("ON_TIME"));
		}
		finally {
			jdbc.update("update session_fee_setting set grace_until = 10 where session_id = ?", currentSessionId());
		}
	}

	@Test
	void childWithoutAPlanGetsAnEmptyAnswerNotAnError() throws Exception {
		long ishaan = addStudent("A-2026-102", "2026-04-01");

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
		pay("2500", "800");
		// A correction now cancels the whole receipt: both lines of April's payment come back as unpaid.
		post(owner, "/api/v1/students/" + aryan + "/payment-corrections",
				"{\"receiptNo\":\"R-2026-0001\",\"reason\":\"WRONG_AMOUNT\",\"note\":\"Paid by mistake\"}")
			.andExpect(status().isCreated());

		fees(office).andExpect(jsonPath("$.pendingNow").value(3300.0))
			.andExpect(jsonPath("$.remainingThisYear").value(38800.0))
			.andExpect(jsonPath("$.payments", hasSize(4)));
	}

}

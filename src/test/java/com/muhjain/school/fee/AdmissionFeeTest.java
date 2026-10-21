package com.muhjain.school.fee;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rule 17 of phase 7: the admission saves the fee plan and the first payment in the same transaction.
 * Fees are a month (db-design, docs/08-decisions.md): {@code schoolFee} and {@code bus.busFee} are both
 * monthly amounts, and {@code fee_due} has one row per child per calendar month of the session (Apr to Mar).
 */
class AdmissionFeeTest extends FeeTestBase {

	long[] route;

	@BeforeEach
	void admitOnFirstApril() {
		clock.setInstant(Instant.parse("2026-04-01T04:30:00Z"));
		route = addBusRoute("Route 4");
	}

	private String admission(String extra) {
		return "{\"name\":\"Aryan Jain\",\"dob\":\"2018-05-14\",\"gender\":\"M\",\"className\":\"3\","
				+ "\"village\":\"Jakhal\",\"fatherOccupation\":\"OTHER\",\"guardians\":[{\"name\":\"Ramesh\","
				+ "\"phone\":\"9812340208\",\"relation\":\"FATHER\"}]" + ((extra == null) ? "" : "," + extra) + "}";
	}

	private String bus(String fee) {
		return "\"bus\":{\"routeId\":" + route[0] + ",\"stopId\":" + route[1] + ((fee == null) ? "" : ",\"busFee\":" + fee)
				+ "}";
	}

	// schoolFee is a month. The session (2026-27) runs 12 months, Apr to Mar.
	private static final String PLAN = "\"fee\":{\"schoolFee\":2500,\"payFrequency\":\"QUARTERLY\"}";

	private static final String FIRST_PAYMENT = "\"firstPayment\":{\"mode\":\"UPI\",\"lines\":[{\"feeHead\":\"SCHOOL\","
			+ "\"amount\":2500},{\"feeHead\":\"BUS\",\"amount\":900}]}";

	private int count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	@Test
	void admissionWithFirstPaymentReturnsReceipt() throws Exception {
		// Admit on 1 April with school 2,500 and bus 900 a month, April paid in full.
		String body = post(desk, "/api/v1/admissions", admission(bus("900") + "," + PLAN + "," + FIRST_PAYMENT))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.receiptNo").value("R-2026-0001"))
			.andExpect(jsonPath("$.stillToPay").value(36500.0))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long studentId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.studentId")).longValue();

		get(desk, "/api/v1/students/" + studentId + "/fees").andExpect(jsonPath("$.pendingNow").value(0.0))
			.andExpect(jsonPath("$.remainingThisYear").value(36500.0))
			.andExpect(jsonPath("$.nextDueOn").value("2026-05-01"))
			.andExpect(jsonPath("$.nextDueAmount").value(3400.0))
			.andExpect(jsonPath("$.dues", hasSize(12)))
			.andExpect(jsonPath("$.payments", hasSize(2)));
	}

	@Test
	void admissionWithAPlanAndNoPaymentShowsTheWholeAmountStillToPay() throws Exception {
		// bus(null): a route is set but no busFee is typed, so it falls back to the session's standard
		// bus fee (800 a month, 11 bus months) = 8,800 for the year; school 2,500 x 12 = 30,000.
		post(desk, "/api/v1/admissions", admission(bus(null) + "," + PLAN)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.receiptNo").value(nullValue()))
			.andExpect(jsonPath("$.stillToPay").value(38800.0));
		assertThat(count("fee_payment")).isZero();
	}

	@Test
	void admissionWithoutFeeStillWorksAndMakesNoPlan() throws Exception {
		post(desk, "/api/v1/admissions", admission(null)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.receiptNo").value(nullValue()))
			.andExpect(jsonPath("$.stillToPay").value(nullValue()));
		assertThat(count("fee_plan")).isZero();
	}

	@Test
	void busFeeOfTheBusPartIsUsedWhenTheFeeHasNone() throws Exception {
		// The fee plan itself never carries a bus fee any more: it always comes from the bus part
		// (here, 900 a month) rather than the session's standard fee (800).
		post(desk, "/api/v1/admissions", admission(bus("900") + ",\"fee\":{\"schoolFee\":2500,"
				+ "\"payFrequency\":\"YEARLY\"}")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.stillToPay").value(39900.0));
		assertThat(jdbc.queryForObject("select bus_fee_per_month from transport_enrolment", java.math.BigDecimal.class))
			.isEqualByComparingTo("900");
	}

	@Test
	void emptySchoolFeeComesFromTheClassFee() throws Exception {
		put(owner, "/api/v1/sessions/" + currentSessionId() + "/class-fees",
				"{\"fees\":[{\"className\":\"3\",\"perMonth\":2800}]}").andExpect(status().isOk());

		post(desk, "/api/v1/admissions", admission("\"fee\":{\"payFrequency\":\"YEARLY\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.stillToPay").value(33600.0));
	}

	@Test
	void noSchoolFeeAndNoClassFeeStopsTheAdmission() throws Exception {
		post(desk, "/api/v1/admissions", admission("\"fee\":{\"payFrequency\":\"YEARLY\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields['fee.schoolFee']").exists());
		assertThat(count("student")).isZero();
		assertThat(count("number_counter")).isZero();
	}

	@Test
	void firstPaymentWithoutAPlanIsRefused() throws Exception {
		post(desk, "/api/v1/admissions", admission(FIRST_PAYMENT)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.firstPayment").exists());
		assertThat(count("student")).isZero();
	}

	@Test
	void aTooLargeFirstPaymentRollsBackTheWholeAdmission() throws Exception {
		// School cap for the year is 2,500 x 12 = 30,000; one rupee over that is too much.
		String tooMuch = "\"firstPayment\":{\"mode\":\"CASH\",\"lines\":[{\"feeHead\":\"SCHOOL\",\"amount\":30001}]}";

		post(desk, "/api/v1/admissions", admission(bus("900") + "," + PLAN + "," + tooMuch))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("PAYMENT_TOO_LARGE"));

		assertThat(count("student")).isZero();
		assertThat(count("guardian")).isZero();
		assertThat(count("transport_enrolment")).isZero();
		assertThat(count("fee_plan")).isZero();
		assertThat(count("fee_due")).isZero();
		assertThat(count("fee_payment")).isZero();
		assertThat(count("number_counter")).isZero();
	}

	@Test
	void transportInchargeCannotAdmit() throws Exception {
		post(transport, "/api/v1/admissions", admission(PLAN)).andExpect(status().isForbidden());
	}

}

package com.muhjain.school.fee;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rule 17 of phase 7: the admission saves the fee plan and the first payment in the same transaction. */
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

	private static final String PLAN = "\"fee\":{\"schoolFee\":30000,\"busFee\":8800,\"payFrequency\":\"QUARTERLY\"}";

	private static final String FIRST_PAYMENT = "\"firstPayment\":{\"mode\":\"UPI\",\"lines\":[{\"feeHead\":\"SCHOOL\","
			+ "\"amount\":7500},{\"feeHead\":\"BUS\",\"amount\":2200}]}";

	private int count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	@Test
	void admissionWithFirstPaymentReturnsReceipt() throws Exception {
		// Done when: admit on 1 April with 30,000 + 8,800 quarterly and 9,700 paid.
		String body = post(desk, "/api/v1/admissions", admission(bus("8800") + "," + PLAN + "," + FIRST_PAYMENT))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.receiptNo").value("R-2026-0001"))
			.andExpect(jsonPath("$.stillToPay").value(29100.0))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long studentId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.studentId")).longValue();

		get(desk, "/api/v1/students/" + studentId + "/fees").andExpect(jsonPath("$.pendingNow").value(0.0))
			.andExpect(jsonPath("$.remainingThisYear").value(29100.0))
			.andExpect(jsonPath("$.nextDueOn").value("2026-07-01"))
			.andExpect(jsonPath("$.nextDueAmount").value(9700.0))
			.andExpect(jsonPath("$.dues", hasSize(8)))
			.andExpect(jsonPath("$.payments", hasSize(2)));
	}

	@Test
	void admissionWithAPlanAndNoPaymentShowsTheWholeAmountStillToPay() throws Exception {
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
		post(desk, "/api/v1/admissions", admission(bus("8800") + ",\"fee\":{\"schoolFee\":30000,"
				+ "\"payFrequency\":\"YEARLY\"}")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.stillToPay").value(38800.0));
		assertThat(jdbc.queryForObject("select bus_fee from fee_plan", java.math.BigDecimal.class))
			.isEqualByComparingTo("8800");
	}

	@Test
	void emptySchoolFeeComesFromTheClassFee() throws Exception {
		put(owner, "/api/v1/sessions/" + currentSessionId() + "/class-fees",
				"{\"fees\":[{\"className\":\"3\",\"schoolFee\":28000}]}").andExpect(status().isOk());

		post(desk, "/api/v1/admissions", admission("\"fee\":{\"payFrequency\":\"YEARLY\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.stillToPay").value(28000.0));
	}

	@Test
	void noSchoolFeeAndNoClassFeeStopsTheAdmission() throws Exception {
		post(desk, "/api/v1/admissions", admission("\"fee\":{\"payFrequency\":\"YEARLY\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields['fee.schoolFee']").exists());
		assertThat(count("student")).isZero();
		assertThat(count("admission_counter")).isZero();
	}

	@Test
	void firstPaymentWithoutAPlanIsRefused() throws Exception {
		post(desk, "/api/v1/admissions", admission(FIRST_PAYMENT)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.firstPayment").exists());
		assertThat(count("student")).isZero();
	}

	@Test
	void aTooLargeFirstPaymentRollsBackTheWholeAdmission() throws Exception {
		String tooMuch = "\"firstPayment\":{\"mode\":\"CASH\",\"lines\":[{\"feeHead\":\"SCHOOL\",\"amount\":30001}]}";

		post(desk, "/api/v1/admissions", admission(bus("8800") + "," + PLAN + "," + tooMuch))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("PAYMENT_TOO_LARGE"));

		assertThat(count("student")).isZero();
		assertThat(count("guardian")).isZero();
		assertThat(count("transport_enrolment")).isZero();
		assertThat(count("fee_plan")).isZero();
		assertThat(count("fee_due")).isZero();
		assertThat(count("fee_payment")).isZero();
		assertThat(count("admission_counter")).isZero();
		assertThat(count("receipt_counter")).isZero();
	}

	@Test
	void transportInchargeCannotAdmit() throws Exception {
		post(transport, "/api/v1/admissions", admission(PLAN)).andExpect(status().isForbidden());
	}

}

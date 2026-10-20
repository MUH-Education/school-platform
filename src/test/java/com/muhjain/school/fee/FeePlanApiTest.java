package com.muhjain.school.fee;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rules 3 to 7 of phase 7: the plan makes a due for every month of the session, a change keeps paid months. */
class FeePlanApiTest extends FeeTestBase {

	static final String STANDARD_PLAN = "{\"schoolFee\":2500,\"payFrequency\":\"QUARTERLY\"}";

	long aryan;

	@BeforeEach
	void addChild() {
		aryan = addStudent("A-2026-101", "2026-04-01");
	}

	private String url() {
		return "/api/v1/students/" + aryan + "/fee-plan";
	}

	@Test
	void planMakesADueForEveryMonthOfTheSession() throws Exception {
		put(office, url(), STANDARD_PLAN).andExpect(status().isOk())
			.andExpect(jsonPath("$.sessionName").value("2026-27"))
			.andExpect(jsonPath("$.netSchoolFeePerMonth").value(2500.0))
			.andExpect(jsonPath("$.dues", hasSize(12)))
			.andExpect(jsonPath("$.dues[0].month").value("2026-04-01"))
			.andExpect(jsonPath("$.dues[0].schoolAmount").value(2500.0))
			.andExpect(jsonPath("$.dues[0].busAmount").value(0.0))
			.andExpect(jsonPath("$.dues[0].total").value(2500.0))
			.andExpect(jsonPath("$.dues[11].month").value("2027-03-01"));
	}

	@Test
	void childWhoJoinsInNovemberHasDuesFromJoiningMonth() throws Exception {
		long ishaan = addStudent("A-2026-102", "2026-11-02");
		put(desk, "/api/v1/students/" + ishaan + "/fee-plan", "{\"schoolFee\":1000,\"payFrequency\":\"QUARTERLY\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dues", hasSize(5)))
			.andExpect(jsonPath("$.dues[0].month").value("2026-11-01"))
			.andExpect(jsonPath("$.dues[0].total").value(1000.0))
			.andExpect(jsonPath("$.dues[4].month").value("2027-03-01"));
	}

	@Test
	void discountIsTakenOffTheSchoolFee() throws Exception {
		put(office, url(), "{\"schoolFee\":2500,\"discount\":200,\"discountReason\":\"SIBLING\","
				+ "\"payFrequency\":\"YEARLY\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.netSchoolFeePerMonth").value(2300.0))
			.andExpect(jsonPath("$.dues", hasSize(12)))
			.andExpect(jsonPath("$.dues[0].schoolAmount").value(2500.0))
			.andExpect(jsonPath("$.dues[0].discountAmount").value(200.0))
			.andExpect(jsonPath("$.dues[0].total").value(2300.0));
	}

	@Test
	void discountWithoutReasonIsRejected() throws Exception {
		put(office, url(), "{\"schoolFee\":2500,\"discount\":200,\"payFrequency\":\"YEARLY\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$.fields.discountReason").exists());
		put(office, url(), "{\"schoolFee\":2500,\"discount\":200,\"discountReason\":\"NONE\","
				+ "\"payFrequency\":\"YEARLY\"}").andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("select count(*) from fee_plan", Integer.class)).isZero();
	}

	@Test
	void discountAboveTheSchoolFeeAndBadMoneyAreRejected() throws Exception {
		put(office, url(), "{\"schoolFee\":1000,\"discount\":2000,\"discountReason\":\"OTHER\","
				+ "\"payFrequency\":\"YEARLY\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.discount").exists());
		put(office, url(), "{\"schoolFee\":-5,\"payFrequency\":\"YEARLY\"}").andExpect(status().isBadRequest());
		put(office, url(), "{\"schoolFee\":10.123,\"payFrequency\":\"YEARLY\"}").andExpect(status().isBadRequest());
		put(office, url(), "{\"schoolFee\":1000}").andExpect(status().isBadRequest());
	}

	@Test
	void changingPlanKeepsPaidMonths() throws Exception {
		put(office, url(), STANDARD_PLAN).andExpect(status().isOk());
		// April (the oldest unpaid month) is paid in full.
		post(office, "/api/v1/students/" + aryan + "/payments",
				"{\"mode\":\"UPI\",\"lines\":[{\"feeHead\":\"SCHOOL\",\"amount\":2500}]}")
			.andExpect(status().isCreated());
		long firstDueId = jdbc.queryForObject(
				"select id from fee_due where student_id = ? and month = date '2026-04-01'", Long.class, aryan);

		// A new slice from today (7 Oct, so "today's month" is October): May-Sep still follow the April
		// slice (2500, untouched), October-March follow the new one (3000, discount 500).
		put(office, url(), "{\"schoolFee\":3000,\"discount\":500,\"discountReason\":\"STAFF_CHILD\","
				+ "\"payFrequency\":\"YEARLY\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.dues", hasSize(12)));

		// Old rows are never changed: both plan slices stay, and April's paid due is the same row, untouched.
		assertThat(jdbc.queryForObject("select count(*) from fee_plan where student_id = ?", Integer.class, aryan))
			.isEqualTo(2);
		assertThat(jdbc.queryForObject("select school_amount from fee_due where id = ?", BigDecimal.class,
				firstDueId)).isEqualByComparingTo("2500");
		assertThat(jdbc.queryForObject(
				"select school_amount from fee_due where student_id = ? and month = date '2026-05-01'",
				BigDecimal.class, aryan)).isEqualByComparingTo("2500");
		assertThat(jdbc.queryForObject(
				"select school_amount from fee_due where student_id = ? and month = date '2026-10-01'",
				BigDecimal.class, aryan)).isEqualByComparingTo("3000");
		assertThat(jdbc.queryForObject(
				"select discount_amount from fee_due where student_id = ? and month = date '2026-10-01'",
				BigDecimal.class, aryan)).isEqualByComparingTo("500");
		assertThat(jdbc.queryForObject("select sum(amount) from fee_payment where student_id = ?", BigDecimal.class,
				aryan)).isEqualByComparingTo("2500");
	}

	@Test
	void unknownChildIs404() throws Exception {
		put(office, "/api/v1/students/99999/fee-plan", STANDARD_PLAN).andExpect(status().isNotFound());
	}

	@Test
	void everyPlanSaveIsWrittenToTheChangeHistory() throws Exception {
		put(office, url(), STANDARD_PLAN).andExpect(status().isOk());
		put(office, url(), STANDARD_PLAN.replace("2500", "2600")).andExpect(status().isOk());

		assertThat(jdbc.queryForList("select action, summary from audit_log where entity_type = 'STUDENT' "
				+ "and entity_id = ? order by id", aryan))
			.extracting(r -> r.get("action") + ": " + r.get("summary"))
			.containsExactly("CREATED: Fee plan set: school ₹2,500 a month, from 2026-04-01, QUARTERLY",
					"UPDATED: Fee plan changed from school ₹2,500 a month, from 2026-04-01, QUARTERLY to school "
							+ "₹2,600 a month, from 2026-10-01, QUARTERLY");
	}

	@Test
	void transportInchargeCannotSavePlan() throws Exception {
		put(transport, url(), STANDARD_PLAN).andExpect(status().isForbidden());
	}

}

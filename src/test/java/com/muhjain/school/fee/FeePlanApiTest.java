package com.muhjain.school.fee;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rules 3 to 7 of phase 7: the plan makes the dues, a change makes them again and keeps the payments. */
class FeePlanApiTest extends FeeTestBase {

	static final String QUARTERLY = "{\"schoolFee\":30000,\"busFee\":8800,\"payFrequency\":\"QUARTERLY\"}";

	long aryan;

	@BeforeEach
	void addChild() {
		aryan = addStudent("A-2026-1", "2026-04-01");
	}

	private String url() {
		return "/api/v1/students/" + aryan + "/fee-plan";
	}

	@Test
	void quarterlyPlanMakesEightDues() throws Exception {
		put(office, url(), QUARTERLY).andExpect(status().isOk())
			.andExpect(jsonPath("$.sessionName").value("2026-27"))
			.andExpect(jsonPath("$.netSchoolFee").value(30000.0))
			.andExpect(jsonPath("$.dues", hasSize(8)))
			.andExpect(jsonPath("$.dues[0].dueOn").value("2026-04-01"))
			.andExpect(jsonPath("$.dues[0].feeHead").value("SCHOOL"))
			.andExpect(jsonPath("$.dues[0].amount").value(7500.0))
			.andExpect(jsonPath("$.dues[1].feeHead").value("BUS"))
			.andExpect(jsonPath("$.dues[1].amount").value(2200.0));
	}

	@Test
	void childWhoJoinsInNovemberHasDuesFromJoiningDay() throws Exception {
		long ishaan = addStudent("A-2026-2", "2026-11-02");
		put(desk, "/api/v1/students/" + ishaan + "/fee-plan",
				"{\"schoolFee\":12000,\"payFrequency\":\"QUARTERLY\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.dues", hasSize(2)))
			.andExpect(jsonPath("$.dues[0].dueOn").value("2026-11-02"))
			.andExpect(jsonPath("$.dues[0].amount").value(6000.0))
			.andExpect(jsonPath("$.dues[1].dueOn").value("2027-01-01"));
	}

	@Test
	void discountIsTakenOffTheSchoolFee() throws Exception {
		put(office, url(), "{\"schoolFee\":30000,\"discount\":2000,\"discountReason\":\"SIBLING\","
				+ "\"payFrequency\":\"YEARLY\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.netSchoolFee").value(28000.0))
			.andExpect(jsonPath("$.dues", hasSize(1)))
			.andExpect(jsonPath("$.dues[0].amount").value(28000.0));
	}

	@Test
	void discountWithoutReasonIsRejected() throws Exception {
		put(office, url(), "{\"schoolFee\":30000,\"discount\":2000,\"payFrequency\":\"YEARLY\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$.fields.discountReason").exists());
		put(office, url(), "{\"schoolFee\":30000,\"discount\":2000,\"discountReason\":\"NONE\","
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
	void changingPlanKeepsPayments() throws Exception {
		put(office, url(), QUARTERLY).andExpect(status().isOk());
		jdbc.update("insert into fee_payment (student_id, session_id, fee_head, amount, paid_on, mode, receipt_no) "
				+ "values (?, ?, 'SCHOOL', 7500, date '2026-04-01', 'UPI', 'R-2026-0001')", aryan,
				currentSessionId());
		long planId = jdbc.queryForObject("select id from fee_plan", Long.class);
		Long firstDueId = jdbc.queryForObject("select min(id) from fee_due", Long.class);

		// Yearly now, and a discount. The 8 dues go, one new due is made, the payment stays.
		put(office, url(), "{\"schoolFee\":30000,\"discount\":1000,\"discountReason\":\"STAFF_CHILD\","
				+ "\"payFrequency\":\"YEARLY\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.dues", hasSize(1)));

		assertThat(jdbc.queryForObject("select count(*) from fee_due where fee_plan_id = ?", Integer.class, planId))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from fee_due where id = ?", Integer.class, firstDueId))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from fee_plan", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select sum(amount) from fee_payment", BigDecimal.class))
			.isEqualByComparingTo("7500");
	}

	@Test
	void unknownChildIs404() throws Exception {
		put(office, "/api/v1/students/99999/fee-plan", QUARTERLY).andExpect(status().isNotFound());
	}

	@Test
	void everyPlanSaveIsWrittenToTheChangeHistory() throws Exception {
		put(office, url(), QUARTERLY).andExpect(status().isOk());
		put(office, url(), QUARTERLY.replace("30000", "31000")).andExpect(status().isOk());

		assertThat(jdbc.queryForList("select action, summary from audit_log where entity_type = 'STUDENT' "
				+ "and entity_id = ? order by id", aryan))
			.extracting(r -> r.get("action") + ": " + r.get("summary"))
			.containsExactly("CREATED: Fee plan set: school ₹30,000, bus ₹8,800, QUARTERLY",
					"UPDATED: Fee plan changed from school ₹30,000, bus ₹8,800, QUARTERLY to school ₹31,000, "
							+ "bus ₹8,800, QUARTERLY");
	}

	@Test
	void transportInchargeCannotSavePlan() throws Exception {
		put(transport, url(), QUARTERLY).andExpect(status().isForbidden());
	}

}

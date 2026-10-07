package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rules 9 to 14 of phase 7. Today is 7 Oct 2026. Aryan owes school 30,000 and bus 8,800, quarterly, from 1 Apr. */
class PaymentApiTest extends FeeTestBase {

	long aryan;

	@BeforeEach
	void addChildWithPlan() throws Exception {
		aryan = addStudent("A-2026-1", "2026-04-01");
		put(office, "/api/v1/students/" + aryan + "/fee-plan", FeePlanApiTest.QUARTERLY).andExpect(status().isOk());
	}

	private String payments() {
		return "/api/v1/students/" + aryan + "/payments";
	}

	private static String upi(String school, String bus) {
		List<String> lines = new ArrayList<>();
		if (school != null) {
			lines.add("{\"feeHead\":\"SCHOOL\",\"amount\":" + school + "}");
		}
		if (bus != null) {
			lines.add("{\"feeHead\":\"BUS\",\"amount\":" + bus + "}");
		}
		return "{\"mode\":\"UPI\",\"lines\":[" + String.join(",", lines) + "]}";
	}

	private BigDecimal paidTotal() {
		return jdbc.queryForObject("select coalesce(sum(amount), 0) from fee_payment", BigDecimal.class);
	}

	@Test
	void oneReceiptCanCoverSchoolAndBus() throws Exception {
		post(office, payments(), upi("7500", "2200")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.receiptNo").value("R-2026-0001"))
			.andExpect(jsonPath("$.total").value(9700.0))
			.andExpect(jsonPath("$.stillToPay").value(29100.0))
			.andExpect(jsonPath("$.payments", hasSize(2)))
			.andExpect(jsonPath("$.payments[0].feeHead").value("SCHOOL"))
			.andExpect(jsonPath("$.payments[1].feeHead").value("BUS"));

		assertThat(jdbc.queryForList("select distinct receipt_no from fee_payment", String.class))
			.containsExactly("R-2026-0001");
		assertThat(jdbc.queryForObject("select count(*) from fee_payment", Integer.class)).isEqualTo(2);
		post(desk, payments(), upi("100", null)).andExpect(jsonPath("$.receiptNo").value("R-2026-0002"));
	}

	@Test
	void paymentLargerThanPendingIsRejected() throws Exception {
		post(office, payments(), upi("30001", null)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("PAYMENT_TOO_LARGE"));
		// Bus is too large, school is fine: nothing at all is saved, and no receipt number is used.
		post(office, payments(), upi("100", "8801")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("PAYMENT_TOO_LARGE"));
		assertThat(paidTotal()).isEqualByComparingTo("0");
		post(office, payments(), upi("100", null)).andExpect(jsonPath("$.receiptNo").value("R-2026-0001"));

		// After paying the whole school fee, one more rupee is too much.
		post(office, payments(), upi("29900", null)).andExpect(status().isCreated());
		post(office, payments(), upi("1", null)).andExpect(status().isConflict());
	}

	@Test
	void payingNextQuarterEarlyIsAllowed() throws Exception {
		// 7 Oct: three school dues are due (22,500). Paying 25,000 also pays most of the January due.
		post(office, payments(), upi("25000", null)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.stillToPay").value(13800.0));
	}

	@Test
	void badPaymentsAreRejected() throws Exception {
		post(office, payments(), "{\"mode\":\"UPI\",\"lines\":[]}").andExpect(status().isBadRequest());
		post(office, payments(), upi("0", null)).andExpect(status().isBadRequest());
		post(office, payments(), upi("-5", null)).andExpect(status().isBadRequest());
		post(office, payments(), upi("10.123", null)).andExpect(status().isBadRequest());
		post(office, payments(), "{\"lines\":[{\"feeHead\":\"SCHOOL\",\"amount\":10}]}")
			.andExpect(status().isBadRequest());
		post(office, payments(), "{\"mode\":\"CARD\",\"lines\":[{\"feeHead\":\"SCHOOL\",\"amount\":10}]}")
			.andExpect(status().isBadRequest());
		post(office, payments(), "{\"mode\":\"UPI\",\"lines\":[{\"feeHead\":\"SCHOOL\",\"amount\":10},"
				+ "{\"feeHead\":\"SCHOOL\",\"amount\":20}]}").andExpect(status().isBadRequest());
		post(office, payments(), "{\"mode\":\"UPI\",\"paidOn\":\"2026-10-08\",\"lines\":[{\"feeHead\":\"SCHOOL\","
				+ "\"amount\":10}]}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.paidOn").exists());
		assertThat(paidTotal()).isEqualByComparingTo("0");
	}

	@Test
	void paymentNeedsAPlanAndAnExistingChild() throws Exception {
		long ishaan = addStudent("A-2026-2", "2026-04-01");
		post(office, "/api/v1/students/" + ishaan + "/payments", upi("100", null)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("NO_FEE_PLAN"));
		post(office, "/api/v1/students/99999/payments", upi("100", null)).andExpect(status().isNotFound());
	}

	@Test
	void paymentKeepsDateModeNoteAndWhoTypedIt() throws Exception {
		post(desk, payments(), "{\"mode\":\"CHEQUE\",\"paidOn\":\"2026-10-05\",\"note\":\"Cheque 000123\","
				+ "\"lines\":[{\"feeHead\":\"BUS\",\"amount\":2200}]}").andExpect(status().isCreated());

		var row = jdbc.queryForMap("select paid_on::text as paid_on, mode, note, recorded_by from fee_payment");
		assertThat(row.get("paid_on")).isEqualTo("2026-10-05");
		assertThat(row.get("mode")).isEqualTo("CHEQUE");
		assertThat(row.get("note")).isEqualTo("Cheque 000123");
		assertThat(row.get("recorded_by")).isEqualTo(
				jdbc.queryForObject("select id from app_user where phone = '+919812340004'", Long.class));
	}

	@Test
	void everyPaymentIsInTheChangeHistory() throws Exception {
		post(office, payments(), upi("7500", "2200")).andExpect(status().isCreated());

		assertThat(jdbc.queryForList("select summary from audit_log where entity_type = 'STUDENT' and entity_id = ? "
				+ "and summary like 'Payment%' order by id", String.class, aryan))
			.containsExactly("Payment R-2026-0001: SCHOOL ₹7,500 by UPI on 7 Oct 2026.",
					"Payment R-2026-0001: BUS ₹2,200 by UPI on 7 Oct 2026.");
	}

	@Test
	void paymentsCannotBeEditedOrDeleted() throws Exception {
		post(office, payments(), upi("100", null)).andExpect(status().isCreated());
		for (var method : List.of("PUT", "PATCH", "DELETE")) {
			mockMvc.perform(MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),
					payments() + "/1")
				.header("Authorization", bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().is4xxClientError());
		}
		assertThat(paidTotal()).isEqualByComparingTo("100");
	}

	@Test
	void twentyPaymentsAtOnceGetTwentyReceiptNumbers() throws Exception {
		int count = 20;
		ExecutorService pool = Executors.newFixedThreadPool(count);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Integer>> codes = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			codes.add(pool.submit(() -> {
				go.await();
				return post(office, payments(), upi("100", null)).andReturn().getResponse().getStatus();
			}));
		}
		go.countDown();
		for (Future<Integer> code : codes) {
			assertThat(code.get()).isEqualTo(201);
		}
		pool.shutdown();

		List<String> receipts = jdbc.queryForList("select receipt_no from fee_payment", String.class);
		assertThat(receipts).hasSize(count).doesNotHaveDuplicates();
		assertThat(receipts).contains("R-2026-0001", "R-2026-0020");
		assertThat(paidTotal()).isEqualByComparingTo("2000");
	}

	@Test
	void twoClerksCannotBothPayTheLastRupees() throws Exception {
		post(office, payments(), upi("29900", null)).andExpect(status().isCreated());
		// Only 100 is unpaid for school. Ten clerks each try 20: five can pass, five must get PAYMENT_TOO_LARGE.
		ExecutorService pool = Executors.newFixedThreadPool(10);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Integer>> codes = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			codes.add(pool.submit(() -> {
				go.await();
				return post(office, payments(), upi("20", null)).andReturn().getResponse().getStatus();
			}));
		}
		go.countDown();
		int created = 0;
		for (Future<Integer> code : codes) {
			if (code.get() == 201) {
				created++;
			}
		}
		pool.shutdown();

		assertThat(created).isEqualTo(5);
		assertThat(paidTotal()).isEqualByComparingTo("30000");
	}

	// ---- corrections (rule 13, decision B21) ----

	private String corrections() {
		return "/api/v1/students/" + aryan + "/payment-corrections";
	}

	private static String correction(String receipt, String head, String amount, String note) {
		return "{\"receiptNo\":\"" + receipt + "\",\"feeHead\":\"" + head + "\",\"amount\":" + amount + ",\"note\":\""
				+ note + "\"}";
	}

	@Test
	void officeAdminCannotAddCorrection() throws Exception {
		post(office, payments(), upi("7500", null)).andExpect(status().isCreated());
		post(office, corrections(), correction("R-2026-0001", "SCHOOL", "-500", "Typed wrong"))
			.andExpect(status().isForbidden());
		post(desk, corrections(), correction("R-2026-0001", "SCHOOL", "-500", "Typed wrong"))
			.andExpect(status().isForbidden());
		assertThat(paidTotal()).isEqualByComparingTo("7500");
	}

	@Test
	void ownerCorrectionIsANewNegativeRowAndThePaymentStays() throws Exception {
		post(office, payments(), upi("7500", "2200")).andExpect(status().isCreated());
		post(owner, corrections(), correction("R-2026-0001", "SCHOOL", "-500", "Typed 7500, paid 7000"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.receiptNo").value("R-2026-0001"))
			.andExpect(jsonPath("$.total").value(-500.0))
			.andExpect(jsonPath("$.stillToPay").value(29600.0))
			.andExpect(jsonPath("$.payments[0].mode").value("UPI"));

		assertThat(jdbc.queryForObject("select count(*) from fee_payment", Integer.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select amount from fee_payment where fee_head = 'SCHOOL' and amount > 0",
				BigDecimal.class)).isEqualByComparingTo("7500");
		assertThat(jdbc.queryForObject("select note from fee_payment where amount < 0", String.class))
			.isEqualTo("Typed 7500, paid 7000");
		assertThat(jdbc.queryForList("select summary from audit_log where summary like 'Correction%'", String.class))
			.containsExactly("Correction on R-2026-0001: SCHOOL -₹500. Typed 7500, paid 7000");
	}

	@Test
	void badCorrectionsAreRejected() throws Exception {
		post(office, payments(), upi("7500", null)).andExpect(status().isCreated());
		// Positive amount, no note, unknown receipt, wrong head, more than the receipt holds.
		post(owner, corrections(), correction("R-2026-0001", "SCHOOL", "500", "x")).andExpect(status().isBadRequest());
		post(owner, corrections(), correction("R-2026-0001", "SCHOOL", "-500", " ")).andExpect(status().isBadRequest());
		post(owner, corrections(), correction("R-2026-0099", "SCHOOL", "-500", "x")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.receiptNo").exists());
		post(owner, corrections(), correction("R-2026-0001", "BUS", "-500", "x")).andExpect(status().isBadRequest());
		post(owner, corrections(), correction("R-2026-0001", "SCHOOL", "-7501", "x")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.amount").exists());
		// A second correction counts the first: 7,500 - 7,000 leaves 500, so -501 is too much.
		post(owner, corrections(), correction("R-2026-0001", "SCHOOL", "-7000", "Wrong child")).andExpect(
				status().isCreated());
		post(owner, corrections(), correction("R-2026-0001", "SCHOOL", "-501", "x")).andExpect(status().isBadRequest());
		assertThat(paidTotal()).isEqualByComparingTo("500");
	}

	@Test
	void aReceiptOfAnotherChildCannotBeCorrected() throws Exception {
		post(office, payments(), upi("7500", null)).andExpect(status().isCreated());
		long ishaan = addStudent("A-2026-2", "2026-04-01");
		put(office, "/api/v1/students/" + ishaan + "/fee-plan", FeePlanApiTest.QUARTERLY).andExpect(status().isOk());

		post(owner, "/api/v1/students/" + ishaan + "/payment-corrections",
				correction("R-2026-0001", "SCHOOL", "-500", "x")).andExpect(status().isBadRequest());
		assertThat(paidTotal()).isEqualByComparingTo("7500");
	}

}

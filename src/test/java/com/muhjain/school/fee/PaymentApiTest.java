package com.muhjain.school.fee;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rules 9 to 14 of phase 7. Today is 7 Oct 2026. Aryan owes school 2,500 and bus 800, a month, from 1 Apr
 * (school 2,500 x 12 = 30,000 and bus 800 x 11 bus months = 8,800 for the whole session).
 */
class PaymentApiTest extends FeeTestBase {

	long aryan;

	@BeforeEach
	void addChildWithPlan() throws Exception {
		aryan = addStudent("A-2026-101", "2026-04-01");
		long[] route = addBusRoute("Route 4");
		addBusEnrolment(aryan, route, "800", "2026-04-01");
		put(office, "/api/v1/students/" + aryan + "/fee-plan", FeePlanApiTest.STANDARD_PLAN).andExpect(status().isOk());
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
		// Amounts fit inside April's open balance (2,500 school, 800 bus), so each head is one row.
		post(office, payments(), upi("2000", "500")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.receiptNo").value("R-2026-0001"))
			.andExpect(jsonPath("$.total").value(2500.0))
			.andExpect(jsonPath("$.stillToPay").value(36300.0))
			.andExpect(jsonPath("$.payments", hasSize(2)))
			.andExpect(jsonPath("$.payments[0].feeHead").value("SCHOOL"))
			.andExpect(jsonPath("$.payments[1].feeHead").value("BUS"));

		assertThat(jdbc.queryForList(
				"select distinct r.receipt_no from fee_payment fp join receipt r on r.id = fp.receipt_id",
				String.class)).containsExactly("R-2026-0001");
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

		// After paying the whole school fee for the year, one more rupee is too much.
		post(office, payments(), upi("29900", null)).andExpect(status().isCreated());
		post(office, payments(), upi("1", null)).andExpect(status().isConflict());
	}

	@Test
	void payingNextQuarterEarlyIsAllowed() throws Exception {
		// Paying 25,000 of the 30,000 school cap for the year leaves 5,000 school + 8,800 bus = 13,800.
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
		long ishaan = addStudent("A-2026-102", "2026-04-01");
		post(office, "/api/v1/students/" + ishaan + "/payments", upi("100", null)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("NO_FEE_PLAN"));
		post(office, "/api/v1/students/99999/payments", upi("100", null)).andExpect(status().isNotFound());
	}

	@Test
	void paymentKeepsDateModeNoteAndWhoTypedIt() throws Exception {
		// 500 fits inside one month's open bus balance, so there is exactly one receipt to read back.
		post(desk, payments(), "{\"mode\":\"CHEQUE\",\"paidOn\":\"2026-10-05\",\"reference\":\"Cheque 000123\","
				+ "\"lines\":[{\"feeHead\":\"BUS\",\"amount\":500}]}").andExpect(status().isCreated());

		var row = jdbc.queryForMap("select paid_on::text as paid_on, mode, reference, taken_by from receipt");
		assertThat(row.get("paid_on")).isEqualTo("2026-10-05");
		assertThat(row.get("mode")).isEqualTo("CHEQUE");
		assertThat(row.get("reference")).isEqualTo("Cheque 000123");
		assertThat(row.get("taken_by")).isEqualTo(
				jdbc.queryForObject("select id from app_user where phone = '+919812340004'", Long.class));
	}

	@Test
	void everyPaymentIsInTheChangeHistory() throws Exception {
		// Amounts fit inside April's open balance, so each head writes exactly one audit row.
		post(office, payments(), upi("2000", "500")).andExpect(status().isCreated());

		assertThat(jdbc.queryForList("select summary from audit_log where entity_type = 'STUDENT' and entity_id = ? "
				+ "and summary like 'Payment%' order by id", String.class, aryan))
			.containsExactly("Payment R-2026-0001: SCHOOL ₹2,000 for 2026-04-01 by UPI.",
					"Payment R-2026-0001: BUS ₹500 for 2026-04-01 by UPI.");
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
		// Each payment of 100 fits inside April's 2,500 open school balance (20 x 100 = 2,000), so none
		// of them ever splits across months: one row per payment, one receipt per row.
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

		List<String> receipts = jdbc.queryForList(
				"select r.receipt_no from fee_payment fp join receipt r on r.id = fp.receipt_id", String.class);
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

	// ---- corrections: cancel a whole receipt, by the owner only (rule 13) ----

	private String corrections() {
		return "/api/v1/students/" + aryan + "/payment-corrections";
	}

	private static String correction(String receipt, String reason, String note) {
		return "{\"receiptNo\":\"" + receipt + "\",\"reason\":\"" + reason + "\",\"note\":\"" + note + "\"}";
	}

	@Test
	void officeAdminCannotAddCorrection() throws Exception {
		post(office, payments(), upi("2000", null)).andExpect(status().isCreated());
		post(office, corrections(), correction("R-2026-0001", "WRONG_AMOUNT", "Typed wrong"))
			.andExpect(status().isForbidden());
		post(desk, corrections(), correction("R-2026-0001", "WRONG_AMOUNT", "Typed wrong"))
			.andExpect(status().isForbidden());
		assertThat(paidTotal()).isEqualByComparingTo("2000");
	}

	@Test
	void ownerCancelsAWholeReceiptAndTheOriginalPaymentStays() throws Exception {
		// Amounts fit inside April's open balance, so the receipt has exactly 2 lines (one per head).
		post(office, payments(), upi("2000", "500")).andExpect(status().isCreated());
		post(owner, corrections(), correction("R-2026-0001", "WRONG_AMOUNT", "Typed wrong, cancel and redo"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.receiptNo").value("R-2026-0001"))
			.andExpect(jsonPath("$.total").value(-2500.0))
			.andExpect(jsonPath("$.stillToPay").value(38800.0))
			.andExpect(jsonPath("$.payments", hasSize(2)));

		assertThat(jdbc.queryForObject("select count(*) from fee_payment", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select amount from fee_payment where fee_head = 'SCHOOL' and amount > 0",
				BigDecimal.class)).isEqualByComparingTo("2000");
		assertThat(jdbc.queryForObject("select status from receipt where receipt_no = 'R-2026-0001'", String.class))
			.isEqualTo("CANCELLED");
		assertThat(jdbc.queryForList("select summary from audit_log where summary like 'Receipt%'", String.class))
			.containsExactly("Receipt R-2026-0001 cancelled (WRONG_AMOUNT): Typed wrong, cancel and redo");
	}

	@Test
	void badCorrectionsAreRejected() throws Exception {
		post(office, payments(), upi("2000", null)).andExpect(status().isCreated());
		// Blank note, unknown receipt, missing reason.
		post(owner, corrections(), correction("R-2026-0001", "WRONG_AMOUNT", " ")).andExpect(status().isBadRequest());
		post(owner, corrections(), correction("R-2026-0099", "WRONG_AMOUNT", "x")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.receiptNo").exists());
		post(owner, corrections(), "{\"receiptNo\":\"R-2026-0001\",\"note\":\"x\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.reason").exists());

		post(owner, corrections(), correction("R-2026-0001", "WRONG_AMOUNT", "Cancel it")).andExpect(
				status().isCreated());
		// A receipt that is already cancelled cannot be cancelled again.
		post(owner, corrections(), correction("R-2026-0001", "WRONG_AMOUNT", "Again")).andExpect(
				status().isConflict())
			.andExpect(jsonPath("$.error").value("ALREADY_CANCELLED"));
		assertThat(paidTotal()).isEqualByComparingTo("0");
	}

	@Test
	void aReceiptOfAnotherChildCannotBeCorrected() throws Exception {
		post(office, payments(), upi("2000", null)).andExpect(status().isCreated());
		long ishaan = addStudent("A-2026-102", "2026-04-01");
		put(office, "/api/v1/students/" + ishaan + "/fee-plan", FeePlanApiTest.STANDARD_PLAN).andExpect(status().isOk());

		post(owner, "/api/v1/students/" + ishaan + "/payment-corrections",
				correction("R-2026-0001", "WRONG_AMOUNT", "x")).andExpect(status().isNotFound());
		assertThat(paidTotal()).isEqualByComparingTo("2000");
	}

}

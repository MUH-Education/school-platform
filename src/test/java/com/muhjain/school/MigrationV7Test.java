package com.muhjain.school;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The database rules of V7__fees.sql. They hold even if the Java code has a bug. */
@Transactional
class MigrationV7Test extends AbstractIntegrationTest {

	private long sessionId() {
		return jdbc.queryForObject(
				"select id from academic_session where date '2026-10-07' between starts_on and ends_on", Long.class);
	}

	private long addStudent() {
		return jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
				+ "father_occupation, joined_on, created_by) values ('A-2026-101', 'Aryan', date '2018-05-14', 'M', '3', "
				+ "'Jakhal', 'OTHER', date '2026-04-01', ?) returning id", Long.class, anyUserId());
	}

	private long addGuardian() {
		return jdbc.queryForObject(
				"insert into guardian (name, phone) values ('Ramesh', '+919812340208') returning id", Long.class);
	}

	private void addPlan(long studentId, String schoolFee, String discount, String reason) {
		jdbc.update("insert into fee_plan (student_id, session_id, from_month, school_fee_per_month, "
				+ "discount_per_month, discount_reason, created_by) values (?, ?, date '2026-04-01', ?::numeric, "
				+ "?::numeric, ?, ?)", studentId, sessionId(), schoolFee, discount, reason, anyUserId());
	}

	private long addReceipt(long guardianId, String receiptNo) {
		return jdbc.queryForObject("insert into receipt (receipt_no, session_id, family_guardian_id, paid_on, mode, "
				+ "amount, taken_by) values (?, ?, ?, date '2026-07-01', 'UPI', 2500.00, ?) returning id", Long.class,
				receiptNo, sessionId(), guardianId, anyUserId());
	}

	private long addPayment(long receiptId, long studentId, String month, String amount) {
		return jdbc.queryForObject("insert into fee_payment (receipt_id, student_id, session_id, month, fee_head, "
				+ "amount) values (?, ?, ?, ?::date, 'SCHOOL', ?::numeric) returning id", Long.class, receiptId,
				studentId, sessionId(), month, amount);
	}

	@Test
	void discountCannotBeMoreThanTheSchoolFee() {
		long student = addStudent();
		assertThatThrownBy(() -> addPlan(student, "2500", "3000", "SIBLING"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aDiscountNeedsAReasonAndAReasonNeedsADiscount() {
		long student = addStudent();
		assertThatThrownBy(() -> addPlan(student, "2500", "500", "NONE"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> addPlan(student, "2500", "0", "SIBLING"))
			.isInstanceOf(DataIntegrityViolationException.class);
		addPlan(student, "2500", "500", "SIBLING");
	}

	@Test
	void onlyOneFeeDueRowPerChildPerMonth() {
		long student = addStudent();
		jdbc.update("insert into fee_due (student_id, session_id, month, school_amount) values (?, ?, "
				+ "date '2026-07-01', 2500.00)", student, sessionId());
		assertThatThrownBy(() -> jdbc.update("insert into fee_due (student_id, session_id, month, school_amount) "
				+ "values (?, ?, date '2026-07-01', 2500.00)", student, sessionId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void receiptNumberMustLookLikeRxxxxOrRFxxxx() {
		long guardian = addGuardian();
		assertThatThrownBy(() -> addReceipt(guardian, "R-2026-1")).isInstanceOf(DataIntegrityViolationException.class);
		addReceipt(guardian, "R-2026-0001");
	}

	@Test
	void feePaymentRowsCanNeverBeChangedOrDeleted() {
		long student = addStudent();
		long guardian = addGuardian();
		long receipt = addReceipt(guardian, "R-2026-0001");
		long payment = addPayment(receipt, student, "2026-07-01", "2500.00");

		assertThatThrownBy(() -> jdbc.update("update fee_payment set amount = 1 where id = ?", payment))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("delete from fee_payment where id = ?", payment))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aReversalLineMustBeNegative() {
		long student = addStudent();
		long guardian = addGuardian();
		long receipt = addReceipt(guardian, "R-2026-0001");
		long payment = addPayment(receipt, student, "2026-07-01", "2500.00");

		assertThatThrownBy(() -> jdbc.update("insert into fee_payment (receipt_id, student_id, session_id, month, "
				+ "fee_head, amount, reversal_of_id) values (?, ?, ?, date '2026-07-01', 'SCHOOL', 2500.00, ?)",
				receipt, student, sessionId(), payment)).isInstanceOf(DataIntegrityViolationException.class);

		jdbc.update("insert into fee_payment (receipt_id, student_id, session_id, month, fee_head, amount, "
				+ "reversal_of_id) values (?, ?, ?, date '2026-07-01', 'SCHOOL', -2500.00, ?)", receipt, student,
				sessionId(), payment);
		assertThat(jdbc.queryForObject("select sum(amount) from fee_payment where student_id = ?",
				java.math.BigDecimal.class, student)).isEqualByComparingTo("0");
	}

	@Test
	void familyCreditEntryRowsCanNeverBeChangedOrDeleted() {
		long guardian = addGuardian();
		long entry = jdbc.queryForObject("insert into family_credit_entry (guardian_id, amount, reason, created_by) "
				+ "values (?, 6600.00, 'PAID_AHEAD_KEPT', ?) returning id", Long.class, guardian, anyUserId());

		assertThatThrownBy(() -> jdbc.update("update family_credit_entry set amount = 1 where id = ?", entry))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("delete from family_credit_entry where id = ?", entry))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}

package com.muhjain.school;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The database rules of V7__fees.sql. They hold even if the Java code has a bug. */
class MigrationV7Test extends AbstractIntegrationTest {

	private long sessionId() {
		return jdbc.queryForObject("select id from academic_session where is_current", Long.class);
	}

	private long addStudent() {
		return jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
				+ "father_occupation, joined_on) values ('A-2026-1', 'Aryan', date '2018-05-14', 'M', '3', 'Jakhal', "
				+ "'OTHER', date '2026-04-01') returning id", Long.class);
	}

	private void addPlan(long studentId, String school, String discount, String reason) {
		jdbc.update("insert into fee_plan (student_id, session_id, school_fee, bus_fee, discount, discount_reason, "
				+ "pay_frequency) values (?, ?, ?::numeric, 0, ?::numeric, ?, 'QUARTERLY')", studentId, sessionId(),
				school, discount, reason);
	}

	@Test
	void currentSessionIsThereAndOnlyOneCanBeCurrent() {
		assertThat(jdbc.queryForObject("select name from academic_session where is_current", String.class))
			.isEqualTo("2026-27");
		assertThatThrownBy(() -> jdbc.update("insert into academic_session (name, starts_on, ends_on, is_current) "
				+ "values ('2027-28', date '2027-04-01', date '2028-03-31', true)"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void oneFeePerClassInASession() {
		jdbc.update("insert into class_fee (session_id, class_name, school_fee) values (?, '3', 30000)", sessionId());
		assertThatThrownBy(() -> jdbc
			.update("insert into class_fee (session_id, class_name, school_fee) values (?, '3', 31000)", sessionId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void onePlanPerChildPerSessionAndDiscountNeedsAReason() {
		long student = addStudent();
		assertThatThrownBy(() -> addPlan(student, "30000", "1000", "NONE"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> addPlan(student, "30000", "40000", "OTHER"))
			.isInstanceOf(DataIntegrityViolationException.class);
		addPlan(student, "30000", "1000", "SIBLING");
		assertThatThrownBy(() -> addPlan(student, "30000", "0", "NONE"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void paymentOfZeroIsRefusedAndACorrectionNeedsANote() {
		long student = addStudent();
		assertThatThrownBy(() -> insertPayment(student, "0", "x")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertPayment(student, "-500", null))
			.isInstanceOf(DataIntegrityViolationException.class);
		insertPayment(student, "-500", "Wrong amount typed");
		insertPayment(student, "7500", null);
		assertThat(jdbc.queryForObject("select sum(amount) from fee_payment", java.math.BigDecimal.class))
			.isEqualByComparingTo("7000");
	}

	private void insertPayment(long student, String amount, String note) {
		jdbc.update("insert into fee_payment (student_id, session_id, fee_head, amount, paid_on, mode, receipt_no, "
				+ "note) values (?, ?, 'SCHOOL', ?::numeric, date '2026-04-01', 'UPI', 'R-2026-0001', ?)", student,
				sessionId(), amount, note);
	}

}

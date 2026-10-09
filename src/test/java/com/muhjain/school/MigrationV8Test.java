package com.muhjain.school;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The database rules of V8__staff_employee.sql. They hold even if the Java code has a bug. */
class MigrationV8Test extends AbstractIntegrationTest {

	private long addTeacher(String name) {
		return addStaff(name, "TEACHER");
	}

	@Test
	void teacherIsAStaffType() {
		assertThat(addTeacher("Sunita")).isPositive();
		assertThatThrownBy(() -> jdbc.update("insert into staff (name, phone, staff_type) "
				+ "values ('X', '+919811100000', 'PRINCIPAL')"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void onlyTheLast4DigitsOfAnIdFit() {
		long sunita = addTeacher("Sunita");
		// A full Aadhaar number does not fit in the column and does not pass the check (question C11).
		assertThatThrownBy(() -> jdbc.update(
				"update staff set id_proof_type = 'AADHAAR', id_proof_last4 = '123412341234' where id = ?", sunita))
			.isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update("update staff set id_proof_type = 'AADHAAR', id_proof_last4 = '4321' where id = ?", sunita);
		assertThat(jdbc.queryForObject("select id_proof_last4 from staff where id = ?", String.class, sunita))
			.isEqualTo("4321");
	}

	@Test
	void anIdTypeWithoutDigitsIsHalfARecord() {
		long sunita = addTeacher("Sunita");
		assertThatThrownBy(
				() -> jdbc.update("update staff set id_proof_type = 'AADHAAR' where id = ?", sunita))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update staff set id_proof_last4 = '4321' where id = ?", sunita))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void theEmergencyPhoneHasTheSameShapeAsEveryPhone() {
		long sunita = addTeacher("Sunita");
		assertThatThrownBy(() -> jdbc.update("update staff set emergency_phone = '9812340099' where id = ?", sunita))
			.isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update("update staff set emergency_phone = '+919812340099' where id = ?", sunita);
	}

	@Test
	void oneClassHasOneClassTeacher() {
		long sunita = addTeacher("Sunita");
		long rekha = addTeacher("Rekha");
		jdbc.update("insert into teacher_profile (staff_id, class_teacher_of) values (?, '3')", sunita);
		assertThatThrownBy(
				() -> jdbc.update("insert into teacher_profile (staff_id, class_teacher_of) values (?, '3')", rekha))
			.isInstanceOf(DataIntegrityViolationException.class);
		// Two teachers with no class at all are fine.
		jdbc.update("insert into teacher_profile (staff_id) values (?)", rekha);
		assertThat(jdbc.queryForObject("select count(*) from teacher_profile", Integer.class)).isEqualTo(2);
	}

	@Test
	void aTeacherHasAtMostOneTeachingFileAndOneSalary() {
		long sunita = addTeacher("Sunita");
		jdbc.update("insert into teacher_profile (staff_id) values (?)", sunita);
		assertThatThrownBy(() -> jdbc.update("insert into teacher_profile (staff_id) values (?)", sunita))
			.isInstanceOf(DataIntegrityViolationException.class);

		jdbc.update("insert into staff_salary (staff_id, monthly_salary) values (?, 18500.00)", sunita);
		assertThatThrownBy(
				() -> jdbc.update("insert into staff_salary (staff_id, monthly_salary) values (?, 19000)", sunita))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aSalaryIsNeverNegative() {
		long sunita = addTeacher("Sunita");
		assertThatThrownBy(
				() -> jdbc.update("insert into staff_salary (staff_id, monthly_salary) values (?, -1)", sunita))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aDriverStillNeedsALicenceAndATeacherStillHasNone() {
		// The licence rule of V2 is untouched: a teacher with no licence is fine.
		assertThat(jdbc.queryForObject("select licence_no from staff where id = ?", String.class,
				addTeacher("Sunita"))).isNull();
		assertThatThrownBy(() -> jdbc.update("insert into staff (name, phone, staff_type) "
				+ "values ('Jagdish', '+919811100000', 'DRIVER')"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}

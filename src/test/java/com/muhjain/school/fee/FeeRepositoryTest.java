package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** The fee entities match the V7 tables (Hibernate runs with ddl-auto=validate) and save and load. */
class FeeRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	AcademicSessionRepository sessions;

	@Autowired
	FeePlanRepository plans;

	@Autowired
	FeeDueRepository dues;

	@Autowired
	FeePaymentRepository payments;

	@Test
	@Transactional
	void planDuesAndPaymentsSaveAndLoad() {
		long student = jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
				+ "father_occupation, joined_on) values ('A-2026-1', 'Aryan', date '2018-05-14', 'M', '3', 'Jakhal', "
				+ "'OTHER', date '2026-04-01') returning id", Long.class);
		AcademicSession session = sessions.findByCurrentTrue().orElseThrow();
		assertThat(session.getName()).isEqualTo("2026-27");

		FeePlan plan = new FeePlan(student, session.getId(), null);
		plan.setSchoolFee(new BigDecimal("30000.00"));
		plan.setPayFrequency(PayFrequency.QUARTERLY);
		plan = plans.saveAndFlush(plan);
		dues.saveAndFlush(new FeeDue(plan.getId(), FeeHead.SCHOOL, LocalDate.of(2026, 7, 1), new BigDecimal("7500")));
		payments.saveAndFlush(new FeePayment(student, session.getId(), FeeHead.SCHOOL, new BigDecimal("7500"),
				LocalDate.of(2026, 4, 1), PaymentMode.UPI, "R-2026-0001", null, null));

		assertThat(plans.findByStudentIdAndSessionId(student, session.getId())).isPresent();
		assertThat(dues.findByFeePlanIdOrderByDueOnAscIdAsc(plan.getId())).hasSize(1);
		assertThat(payments.findByStudentIdAndSessionIdOrderByPaidOnAscIdAsc(student, session.getId())).hasSize(1);
		dues.deleteByFeePlanId(plan.getId());
		assertThat(dues.findByFeePlanIdOrderByDueOnAscIdAsc(plan.getId())).isEmpty();
	}

}

package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import com.muhjain.school.model.entity.AcademicSession;
import com.muhjain.school.model.entity.FeeDue;
import com.muhjain.school.model.entity.FeeMonthView;
import com.muhjain.school.model.entity.FeePayment;
import com.muhjain.school.model.entity.FeePlan;
import com.muhjain.school.model.entity.Receipt;
import com.muhjain.school.model.enums.FeeHead;
import com.muhjain.school.model.enums.PayFrequency;
import com.muhjain.school.model.enums.PaymentMode;
import com.muhjain.school.model.enums.ReceiptKind;
import com.muhjain.school.model.enums.ReceiptSource;
import com.muhjain.school.model.repository.AcademicSessionRepository;
import com.muhjain.school.model.repository.FeeDueRepository;
import com.muhjain.school.model.repository.FeeMonthViewRepository;
import com.muhjain.school.model.repository.FeePaymentRepository;
import com.muhjain.school.model.repository.FeePlanRepository;
import com.muhjain.school.model.repository.ReceiptRepository;

/** The fee entities match the V7 tables (Hibernate runs with ddl-auto=validate) and save and load. */
class FeeRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	AcademicSessionRepository sessions;

	@Autowired
	FeePlanRepository plans;

	@Autowired
	FeeDueRepository dues;

	@Autowired
	ReceiptRepository receipts;

	@Autowired
	FeePaymentRepository payments;

	@Autowired
	FeeMonthViewRepository monthView;

	@Test
	@Transactional
	void planDuesPaymentsAndTheMonthViewSaveAndLoad() {
		long userId = anyUserId();
		long student = jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
				+ "father_occupation, joined_on, created_by) values ('A-2026-101', 'Aryan', date '2018-05-14', 'M', '3', "
				+ "'Jakhal', 'OTHER', date '2026-04-01', ?) returning id", Long.class, userId);
		long guardian = jdbc.queryForObject("insert into guardian (name, phone) values ('Ramesh', '+919812340208') "
				+ "returning id", Long.class);
		AcademicSession session = sessions
			.findFirstByStartsOnLessThanEqualAndEndsOnGreaterThanEqual(LocalDate.of(2026, 10, 7),
					LocalDate.of(2026, 10, 7))
			.orElseThrow();
		assertThat(session.getName()).isEqualTo("2026-27");

		FeePlan plan = new FeePlan(student, session.getId(), LocalDate.of(2026, 4, 1), new BigDecimal("2500.00"),
				false, userId);
		plan.setPayFrequency(PayFrequency.MONTHLY);
		plan = plans.saveAndFlush(plan);
		FeeDue due = dues.saveAndFlush(new FeeDue(student, session.getId(), LocalDate.of(2026, 7, 1),
				new BigDecimal("2500.00"), BigDecimal.ZERO, BigDecimal.ZERO));
		Receipt receipt = receipts.saveAndFlush(new Receipt("R-2026-0001", ReceiptKind.PAYMENT, ReceiptSource.COLLECT,
				session.getId(), guardian, LocalDate.of(2026, 7, 1), Instant.parse("2026-07-01T04:30:00Z"),
				PaymentMode.UPI, null, new BigDecimal("2500.00"), null, userId));
		payments.saveAndFlush(new FeePayment(receipt.getId(), student, session.getId(), LocalDate.of(2026, 7, 1),
				FeeHead.SCHOOL, new BigDecimal("2500.00"), null));

		assertThat(plans.findByStudentIdAndSessionIdOrderByFromMonthAsc(student, session.getId())).hasSize(1);
		assertThat(dues.findByStudentIdAndSessionIdOrderByMonthAsc(student, session.getId())).hasSize(1);
		assertThat(payments.findByStudentIdAndSessionIdOrderByMonthAscIdAsc(student, session.getId())).hasSize(1);

		FeeMonthView row = monthView.findByStudentIdAndSessionIdOrderByMonthAsc(student, session.getId())
			.stream()
			.filter(v -> v.getFeeDueId().equals(due.getId()))
			.findFirst()
			.orElseThrow();
		assertThat(row.getTotal()).isEqualByComparingTo("2500.00");
		assertThat(row.getPaidSchool()).isEqualByComparingTo("2500.00");
		assertThat(row.getBalance()).isEqualByComparingTo("0.00");
	}

}

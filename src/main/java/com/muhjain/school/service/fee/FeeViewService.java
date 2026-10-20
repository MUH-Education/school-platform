package com.muhjain.school.service.fee;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.util.FeeStatusCalculator.Summary;
import com.muhjain.school.service.setting.SettingService;
import com.muhjain.school.service.student.StudentQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.muhjain.school.model.dto.response.DueView;
import com.muhjain.school.model.dto.response.FeePlanSummary;
import com.muhjain.school.model.dto.response.FeesResponse;
import com.muhjain.school.model.dto.response.PaymentResponse;
import com.muhjain.school.model.entity.AcademicSession;
import com.muhjain.school.model.entity.FeeMonthView;
import com.muhjain.school.model.entity.FeePayment;
import com.muhjain.school.model.entity.FeePlan;
import com.muhjain.school.model.repository.FeeMonthViewRepository;
import com.muhjain.school.model.repository.FeePaymentRepository;
import com.muhjain.school.model.repository.FeePlanRepository;
import com.muhjain.school.util.FeeStatusCalculator;

/**
 * "Fees this year" for one child (rule 16 of phase 7). The maths is in {@link FeeStatusCalculator}, reading
 * {@code v_fee_month}; this class only loads the rows and puts the answer together.
 * Example: Aryan owes school 2500 and bus 800 a month. On 7 Oct he has paid through September.
 * pendingNow 0, remainingThisYear the unpaid months, status ON_TIME.
 */
@Service
public class FeeViewService {

	private final FeePlanRepository plans;

	private final FeeMonthViewRepository months;

	private final FeePaymentRepository payments;

	private final AcademicSessionService sessions;

	private final StudentQueryService students;

	private final SettingService settings;

	private final Clock clock;

	public FeeViewService(FeePlanRepository plans, FeeMonthViewRepository months, FeePaymentRepository payments,
			AcademicSessionService sessions, StudentQueryService students, SettingService settings, Clock clock) {
		this.plans = plans;
		this.months = months;
		this.payments = payments;
		this.sessions = sessions;
		this.students = students;
		this.settings = settings;
		this.clock = clock;
	}

	/** @throws ApiException 404 NOT_FOUND (child) */
	@Transactional(readOnly = true)
	public FeesResponse view(Long studentId) {
		if (students.basics(studentId).isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student does not exist.");
		}
		LocalDate today = LocalDate.now(clock);
		AcademicSession session = sessions.current();
		FeePlan plan = plans.findFirstByStudentIdAndSessionIdOrderByFromMonthDesc(studentId, session.getId())
			.orElse(null);
		List<FeePayment> paymentRows = payments.findByStudentIdAndSessionIdOrderByMonthAscIdAsc(studentId,
				session.getId());
		List<PaymentResponse> paymentViews = paymentRows.stream().map(PaymentResponse::of).toList();
		if (plan == null) {
			return new FeesResponse(studentId, session.getId(), session.getName(), today, null, List.of(),
					paymentViews, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, null, null, null);
		}
		List<FeeMonthView> rows = months.findByStudentIdAndSessionIdOrderByMonthAsc(studentId, session.getId());
		int defaultedAfter = Integer.parseInt(settings.value("fees.defaulted_after_days"));
		Summary summary = FeeStatusCalculator.calculate(
				rows.stream().map(FeeStatusService::toInput).toList(), today, defaultedAfter);
		return new FeesResponse(studentId, session.getId(), session.getName(), today, FeePlanSummary.of(plan),
				summary.months().stream().map(DueView::of).toList(), paymentViews,
				summary.heads().values().stream().map(com.muhjain.school.model.dto.response.HeadView::of).toList(),
				summary.pendingNow(), summary.remainingThisYear(), summary.nextDueOn(), summary.nextDueAmount(),
				summary.status());
	}

}

package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.fee.FeeStatusCalculator.DueInput;
import com.muhjain.school.fee.FeeStatusCalculator.Summary;
import com.muhjain.school.setting.SettingService;
import com.muhjain.school.student.StudentQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Fees this year" for one child (rule 16 of phase 7). The maths is in {@link FeeStatusCalculator}; this class
 * only loads the rows and the two settings and puts the answer together.
 * Example: Aryan owes school 30,000 and bus 8,800, quarterly. On 7 Oct he has paid 22,500 school and 6,600 bus.
 * pendingNow 0, remainingThisYear 9,700 (the January dues: school 7,500 + bus 2,200), status ON_TIME.
 */
@Service
public class FeeViewService {

	private final FeePlanRepository plans;

	private final FeeDueRepository dues;

	private final FeePaymentRepository payments;

	private final SessionService sessions;

	private final StudentQueryService students;

	private final SettingService settings;

	private final Clock clock;

	public FeeViewService(FeePlanRepository plans, FeeDueRepository dues, FeePaymentRepository payments,
			SessionService sessions, StudentQueryService students, SettingService settings, Clock clock) {
		this.plans = plans;
		this.dues = dues;
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
		FeePlan plan = plans.findByStudentIdAndSessionId(studentId, session.getId()).orElse(null);
		List<FeePayment> paymentRows = payments.findByStudentIdAndSessionIdOrderByPaidOnAscIdAsc(studentId,
				session.getId());
		List<PaymentResponse> paymentViews = paymentRows.stream().map(PaymentResponse::of).toList();
		if (plan == null) {
			return new FeesResponse(studentId, session.getId(), session.getName(), today, null, List.of(),
					paymentViews, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, null, null, null);
		}
		List<DueInput> dueInputs = dues.findByFeePlanIdOrderByDueOnAscIdAsc(plan.getId())
			.stream()
			.map(d -> new DueInput(d.getId(), d.getFeeHead(), d.getDueOn(), d.getAmount()))
			.toList();
		Map<FeeHead, BigDecimal> paid = new EnumMap<>(FeeHead.class);
		for (FeePayment row : paymentRows) {
			paid.merge(row.getFeeHead(), row.getAmount(), BigDecimal::add);
		}
		Summary summary = FeeStatusCalculator.calculate(dueInputs, paid, today, graceDays(), defaultedAfterDays());
		return new FeesResponse(studentId, session.getId(), session.getName(), today, FeePlanSummary.of(plan),
				summary.dues().stream().map(d -> DueView.of(d, today)).toList(), paymentViews,
				summary.heads().values().stream().map(HeadView::of).toList(), summary.pendingNow(),
				summary.remainingThisYear(), summary.nextDueOn(), summary.nextDueAmount(), summary.status());
	}

	private int graceDays() {
		return Integer.parseInt(settings.value("fees.grace_days"));
	}

	private int defaultedAfterDays() {
		return Integer.parseInt(settings.value("fees.defaulted_after_days"));
	}

}

package com.muhjain.school.service.fee;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.response.StudentBasics;
import com.muhjain.school.model.entity.*;
import com.muhjain.school.model.repository.*;
import com.muhjain.school.service.student.StudentQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Makes {@code fee_due}, month by month, from {@code fee_plan}, {@code transport_enrolment} and
 * {@code session_fee_setting}. Called whenever the plan changes. A month that already has a payment is never
 * touched again ("the server rewrites only the unpaid months").
 * Example: Aryan, class 3, joined 1 Apr → a row for April, May, ... March, school 2500 a month (or the class
 * fee, if the plan follows it), bus 800 in the months the bus runs.
 */
@Service
public class FeeDueGenerator {

	private final FeeDueRepository dues;

	private final FeePlanRepository plans;

	private final SessionFeeSettingRepository sessionSettings;

	private final ClassFeeRepository classFees;

	private final FeePaymentRepository payments;

	private final StudentQueryService students;

	public FeeDueGenerator(FeeDueRepository dues, FeePlanRepository plans,
			SessionFeeSettingRepository sessionSettings, ClassFeeRepository classFees, FeePaymentRepository payments,
			StudentQueryService students) {
		this.dues = dues;
		this.plans = plans;
		this.sessionSettings = sessionSettings;
		this.classFees = classFees;
		this.payments = payments;
		this.students = students;
	}

	/** Rebuilds the unpaid months of this child's plan in this session. Returns the whole schedule, oldest first. */
	@Transactional
	public List<FeeDue> regenerate(StudentBasics student, AcademicSession session) {
		List<FeePlan> slices = plans.findByStudentIdAndSessionIdOrderByFromMonthAsc(student.id(), session.getId());
		if (slices.isEmpty()) {
			return List.of();
		}
		SessionFeeSetting setting = sessionSettings.findBySessionId(session.getId())
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_FEE_SETTINGS",
					"Fee settings are not set for " + session.getName() + " yet."));
		LocalDate start = student.joinedOn().isAfter(session.getStartsOn()) ? student.joinedOn()
				: session.getStartsOn();
		LocalDate month = start.withDayOfMonth(1);
		LocalDate endMonth = session.getEndsOn().withDayOfMonth(1);
		while (!month.isAfter(endMonth)) {
			if (!payments.existsByStudentIdAndMonth(student.id(), month)) {
				FeePlan slice = effectiveSlice(slices, month);
				if (slice != null) {
					apply(student, session, setting, slice, month);
				}
			}
			month = month.plusMonths(1);
		}
		return dues.findByStudentIdAndSessionIdOrderByMonthAsc(student.id(), session.getId());
	}

	private void apply(StudentBasics student, AcademicSession session, SessionFeeSetting setting, FeePlan slice,
			LocalDate month) {
		BigDecimal schoolAmount = slice.isFollowsClassFee()
				? classFees.findBySessionIdAndClassName(session.getId(), student.className())
					.map(ClassFee::getPerMonth)
					.orElse(slice.getSchoolFeePerMonth())
				: slice.getSchoolFeePerMonth();
		BigDecimal busAmount = setting.hasBusFeeInMonth(month.getMonthValue())
				? students.busFeeOn(student.id(), month).orElse(BigDecimal.ZERO) : BigDecimal.ZERO;
		FeeDue row = dues.findByStudentIdAndMonth(student.id(), month).orElse(null);
		if (row == null) {
			dues.save(new FeeDue(student.id(), session.getId(), month, schoolAmount, slice.getDiscountPerMonth(),
					busAmount));
		}
		else {
			row.setSchoolAmount(schoolAmount);
			row.setDiscountAmount(slice.getDiscountPerMonth());
			row.setBusAmount(busAmount);
		}
	}

	// The slice with the biggest fromMonth that is <= month. Slices are oldest first.
	private static FeePlan effectiveSlice(List<FeePlan> slices, LocalDate month) {
		FeePlan chosen = null;
		for (FeePlan slice : slices) {
			if (!slice.getFromMonth().isAfter(month)) {
				chosen = slice;
			}
			else {
				break;
			}
		}
		return chosen;
	}

}

package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.DayText;
import com.muhjain.school.student.StudentBasics;
import com.muhjain.school.student.StudentQueryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The fee plan of a child (rules 3 to 7 of phase 7).
 * <ul>
 * <li>One plan per child per session.</li>
 * <li>The discount comes off the school fee, and a discount above 0 needs a reason.</li>
 * <li>Changing a plan deletes its dues and makes them again from the plan. Payments are never touched.</li>
 * </ul>
 * Example: school ₹30,000, bus ₹8,800, QUARTERLY, child joined 1 Apr → 8 dues. The clerk then gives a ₹2,000
 * sibling discount. The 8 dues are deleted and made again (school 4 × ₹7,000, bus 4 × ₹2,200). The ₹9,700 the family
 * paid on 1 Apr is still there.
 */
@Service
public class FeePlanService {

	private final FeePlanRepository plans;

	private final FeeDueRepository dues;

	private final SessionService sessions;

	private final StudentQueryService students;

	private final AuditService audit;

	public FeePlanService(FeePlanRepository plans, FeeDueRepository dues, SessionService sessions,
			StudentQueryService students, AuditService audit) {
		this.plans = plans;
		this.dues = dues;
		this.sessions = sessions;
		this.students = students;
		this.audit = audit;
	}

	/**
	 * Creates or changes the plan of the current session.
	 *
	 * @throws ApiException 404 NOT_FOUND (child), 400 VALIDATION (discount, joining day), 409 TRY_AGAIN
	 */
	@Transactional
	public FeePlanResponse save(Long studentId, FeePlanRequest request, Long userId) {
		StudentBasics student = requireStudent(studentId);
		AcademicSession session = sessions.current();
		BigDecimal schoolFee = request.schoolFee();
		BigDecimal busFee = (request.busFee() != null) ? request.busFee() : BigDecimal.ZERO;
		BigDecimal discount = (request.discount() != null) ? request.discount() : BigDecimal.ZERO;
		DiscountReason reason = (request.discountReason() != null) ? request.discountReason() : DiscountReason.NONE;
		if (discount.compareTo(schoolFee) > 0) {
			throw ApiException.validation("discount", "cannot be more than the school fee");
		}
		if (discount.signum() > 0 && reason == DiscountReason.NONE) {
			throw ApiException.validation("discountReason", "is needed when there is a discount");
		}
		if (discount.signum() == 0) {
			reason = DiscountReason.NONE;
		}

		Optional<FeePlan> existing = plans.lockByStudentIdAndSessionId(studentId, session.getId());
		FeePlan plan = existing.orElseGet(() -> new FeePlan(studentId, session.getId(), userId));
		String before = existing.map(FeePlanService::describe).orElse(null);
		plan.setSchoolFee(schoolFee);
		plan.setBusFee(busFee);
		plan.setDiscount(discount);
		plan.setDiscountReason(reason);
		plan.setPayFrequency(request.payFrequency());
		try {
			plan = plans.saveAndFlush(plan);
		}
		catch (DataIntegrityViolationException e) {
			// Two clerks made the first plan of the same child at the same moment.
			throw new ApiException(HttpStatus.CONFLICT, "TRY_AGAIN", "Someone saved this plan just now. Please try again.");
		}
		List<FeeDue> made = remakeDues(plan, session, student);

		String after = describe(plan);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("sessionName", session.getName());
		details.put("schoolFee", schoolFee);
		details.put("busFee", busFee);
		details.put("discount", discount);
		details.put("discountReason", reason.name());
		details.put("payFrequency", plan.getPayFrequency().name());
		audit.record("STUDENT", studentId, (before == null) ? AuditAction.CREATED : AuditAction.UPDATED,
				((before == null) ? "Fee plan set: " : "Fee plan changed from " + before + " to ") + after, details);
		return FeePlanResponse.of(plan, session.getName(), made);
	}

	/**
	 * Rule 8: the bus starts later. The amount is added to the plan's bus fee, and BUS dues are added from the start
	 * day, split over the standard due dates after it. Nothing is deleted and no school due is touched.
	 * Example: bus starts 2 Nov, ₹4,000, QUARTERLY → BUS ₹2,000 on 2 Nov and ₹2,000 on 1 Jan.
	 * <p>
	 * Does nothing when there is no amount, when the child has no plan yet (the plan will carry the bus fee when it
	 * is made), or when the bus starts after the end of the current session.
	 */
	@Transactional
	public void addBusFee(Long studentId, BigDecimal amount, LocalDate startsOn, Long userId) {
		if (amount == null || amount.signum() <= 0) {
			return;
		}
		AcademicSession session = sessions.current();
		if (startsOn.isAfter(session.getEndsOn())) {
			return;
		}
		Optional<FeePlan> found = plans.lockByStudentIdAndSessionId(studentId, session.getId());
		if (found.isEmpty()) {
			return;
		}
		FeePlan plan = found.get();
		plan.setBusFee(plan.getBusFee().add(amount));
		plans.saveAndFlush(plan);
		dues.saveAll(DueScheduleBuilder
			.build(FeeHead.BUS, amount, plan.getPayFrequency(), session.getStartsOn(), session.getEndsOn(), startsOn)
			.stream()
			.map(d -> new FeeDue(plan.getId(), d.head(), d.dueOn(), d.amount()))
			.toList());
		audit.record("STUDENT", studentId, AuditAction.UPDATED, "Bus fee " + MoneyText.of(amount)
				+ " added to the fee plan from " + DayText.on(startsOn) + ". Bus fee is now "
				+ MoneyText.of(plan.getBusFee()) + ".",
				Map.of("busFeeAdded", amount, "busFee", plan.getBusFee(), "from", startsOn.toString()));
	}

	/** The plan of the current session, if the child has one. */
	@Transactional(readOnly = true)
	public Optional<FeePlan> currentPlan(Long studentId) {
		return plans.findByStudentIdAndSessionId(studentId, sessions.current().getId());
	}

	// Deletes the old dues and makes them again. The school dues start on the joining day (or the session start).
	// The bus dues start on the first day of the child's bus in the session, or on the joining day if no bus row yet.
	private List<FeeDue> remakeDues(FeePlan plan, AcademicSession session, StudentBasics student) {
		dues.deleteByFeePlanId(plan.getId());
		LocalDate schoolFrom = student.joinedOn().isAfter(session.getStartsOn()) ? student.joinedOn()
				: session.getStartsOn();
		if (schoolFrom.isAfter(session.getEndsOn())) {
			throw ApiException.validation("studentId", "joined after the end of the school year " + session.getName());
		}
		LocalDate busFrom = students.firstBusDay(student.id(), session.getStartsOn(), session.getEndsOn())
			.map(day -> day.isBefore(schoolFrom) ? schoolFrom : day)
			.orElse(schoolFrom);
		List<DueScheduleBuilder.Due> built = new java.util.ArrayList<>(DueScheduleBuilder.build(FeeHead.SCHOOL,
				plan.getSchoolFee().subtract(plan.getDiscount()), plan.getPayFrequency(), session.getStartsOn(),
				session.getEndsOn(), schoolFrom));
		built.addAll(DueScheduleBuilder.build(FeeHead.BUS, plan.getBusFee(), plan.getPayFrequency(),
				session.getStartsOn(), session.getEndsOn(), busFrom));
		built.sort(java.util.Comparator.comparing(DueScheduleBuilder.Due::dueOn)
			.thenComparing(DueScheduleBuilder.Due::head));
		return dues.saveAll(built.stream()
			.map(d -> new FeeDue(plan.getId(), d.head(), d.dueOn(), d.amount()))
			.toList());
	}

	private StudentBasics requireStudent(Long studentId) {
		return students.basics(studentId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student does not exist."));
	}

	private static String describe(FeePlan plan) {
		String text = "school " + MoneyText.of(plan.getSchoolFee()) + ", bus " + MoneyText.of(plan.getBusFee());
		if (plan.getDiscount().signum() > 0) {
			text += ", discount " + MoneyText.of(plan.getDiscount()) + " (" + plan.getDiscountReason() + ")";
		}
		return text + ", " + plan.getPayFrequency();
	}

}

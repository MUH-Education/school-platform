package com.muhjain.school.service.fee;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.AdmissionFeeRequest;
import com.muhjain.school.model.dto.request.FeePlanRequest;
import com.muhjain.school.model.dto.response.FeePlanResponse;
import com.muhjain.school.model.dto.response.StudentBasics;
import com.muhjain.school.model.entity.AcademicSession;
import com.muhjain.school.model.entity.FeeDue;
import com.muhjain.school.model.entity.FeePlan;
import com.muhjain.school.model.entity.SessionFeeSetting;
import com.muhjain.school.model.enums.AuditAction;
import com.muhjain.school.model.enums.DiscountReason;
import com.muhjain.school.model.repository.FeePlanRepository;
import com.muhjain.school.model.repository.SessionFeeSettingRepository;
import com.muhjain.school.service.audit.AuditService;
import com.muhjain.school.service.student.StudentQueryService;
import com.muhjain.school.util.MoneyText;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The fee plan of a child (rules 3 to 7 of phase 7).
 * <ul>
 * <li>A plan is a timeline: each change adds a new, time-sliced row from a month on. Old rows are never
 * changed (db-design, docs/08-decisions.md).</li>
 * <li>The discount comes off the school fee, and a discount above 0 needs a reason.</li>
 * <li>Changing a plan rewrites only its UNPAID months (never the ones already paid).</li>
 * </ul>
 * Example: Ishaan, school 2500 a month from April. In January a sibling joins: a new row from January,
 * discount 250. February and March (unpaid) are rewritten; April to December (paid) are untouched.
 */
@Service
public class FeePlanService {

	private final FeePlanRepository plans;

	private final FeeDueGenerator dueGenerator;

	private final AcademicSessionService sessions;

	private final StudentQueryService students;

	private final AuditService audit;

	private final ClassFeeService classFees;

	private final SessionFeeSettingRepository sessionFeeSettings;

	private final Clock clock;

	public FeePlanService(FeePlanRepository plans, FeeDueGenerator dueGenerator, AcademicSessionService sessions,
			StudentQueryService students, AuditService audit, ClassFeeService classFees,
			SessionFeeSettingRepository sessionFeeSettings, Clock clock) {
		this.classFees = classFees;
		this.plans = plans;
		this.dueGenerator = dueGenerator;
		this.sessions = sessions;
		this.students = students;
		this.audit = audit;
		this.sessionFeeSettings = sessionFeeSettings;
		this.clock = clock;
	}

	/**
	 * The session's standard bus fee a month, used when an admission or a bus change does not type one.
	 *
	 * @throws ApiException 409 NO_FEE_SETTINGS if the current session has none yet
	 */
	@Transactional(readOnly = true)
	public BigDecimal defaultBusFeePerMonth() {
		AcademicSession session = sessions.current();
		SessionFeeSetting setting = sessionFeeSettings.findBySessionId(session.getId())
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_FEE_SETTINGS",
					"Fee settings are not set for " + session.getName() + " yet."));
		return setting.getBusFeePerMonth();
	}

	/**
	 * Creates or changes the plan of the current session. A new time slice starts from the first day of the
	 * current month (or the child's joining month, for the first plan ever).
	 *
	 * @throws ApiException 404 NOT_FOUND (child), 400 VALIDATION (discount, joining day), 409 TRY_AGAIN
	 */
	@Transactional
	public FeePlanResponse save(Long studentId, FeePlanRequest request, Long userId) {
		StudentBasics student = requireStudent(studentId);
		AcademicSession session = sessions.current();
		BigDecimal schoolFee = request.schoolFee();
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

		Optional<FeePlan> existing = plans.lockNewestByStudentIdAndSessionId(studentId, session.getId());
		LocalDate today = LocalDate.now(clock);
		LocalDate schoolStart = student.joinedOn().isAfter(session.getStartsOn()) ? student.joinedOn()
				: session.getStartsOn();
		LocalDate fromMonth = existing.isEmpty() ? schoolStart.withDayOfMonth(1) : today.withDayOfMonth(1);
		if (fromMonth.isAfter(session.getEndsOn())) {
			throw ApiException.validation("studentId", "joined after the end of the school year " + session.getName());
		}
		String before = existing.map(FeePlanService::describe).orElse(null);
		FeePlan plan = new FeePlan(studentId, session.getId(), fromMonth, schoolFee, true, userId);
		plan.setDiscountPerMonth(discount);
		plan.setDiscountReason(reason);
		plan.setPayFrequency(request.payFrequency());
		try {
			plan = plans.saveAndFlush(plan);
		}
		catch (DataIntegrityViolationException e) {
			// Two clerks made the first plan of the same child at the same moment, or saved twice in the same month.
			throw new ApiException(HttpStatus.CONFLICT, "TRY_AGAIN", "Someone saved this plan just now. Please try again.");
		}
		List<FeeDue> made = dueGenerator.regenerate(student, session);

		String after = describe(plan);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("sessionName", session.getName());
		details.put("schoolFee", schoolFee);
		details.put("discount", discount);
		details.put("discountReason", reason.name());
		details.put("payFrequency", plan.getPayFrequency().name());
		audit.record("STUDENT", studentId, (before == null) ? AuditAction.CREATED : AuditAction.UPDATED,
				((before == null) ? "Fee plan set: " : "Fee plan changed from " + before + " to ") + after, details);
		return FeePlanResponse.of(plan, session.getName(), made);
	}

	/**
	 * The plan of a new admission (rule 17). The school fee comes from the class fee when the clerk left it empty.
	 * The bus fee is not set here any more: it lives on the bus part of the admission (transport_enrolment).
	 *
	 * @throws ApiException 400 VALIDATION (no school fee and no standard fee for the class)
	 */
	@Transactional
	public FeePlanResponse saveAtAdmission(Long studentId, String className, AdmissionFeeRequest fee, Long userId) {
		AcademicSession session = sessions.current();
		BigDecimal schoolFee = fee.schoolFee();
		if (schoolFee == null) {
			schoolFee = classFees.standardFee(session.getId(), className)
				.orElseThrow(() -> ApiException.validation("fee.schoolFee",
						"is needed: no standard fee is set for class " + className));
		}
		return save(studentId, new FeePlanRequest(schoolFee, fee.discount(), fee.discountReason(),
				fee.payFrequency()), userId);
	}

	/**
	 * Rebuilds the unpaid months of this child's current-session plan. Called whenever the bus starts, changes
	 * or stops, since the bus fee of unpaid months comes from {@code transport_enrolment}. Does nothing when
	 * the child has no plan yet (the plan will read the bus fee when it is made) or there is no current session.
	 */
	@Transactional
	public void regenerateDues(Long studentId) {
		AcademicSession session;
		try {
			session = sessions.current();
		}
		catch (ApiException ex) {
			return;
		}
		if (plans.findByStudentIdAndSessionIdOrderByFromMonthAsc(studentId, session.getId()).isEmpty()) {
			return;
		}
		students.basics(studentId).ifPresent(student -> dueGenerator.regenerate(student, session));
	}

	/** True when any child has a plan in the current session. The dev data uses it to load once. */
	@Transactional(readOnly = true)
	public boolean anyPlanExists() {
		return !plans.findBySessionId(sessions.current().getId()).isEmpty();
	}

	/** The newest slice of the plan of the current session, if the child has one. */
	@Transactional(readOnly = true)
	public Optional<FeePlan> currentPlan(Long studentId) {
		return plans.findFirstByStudentIdAndSessionIdOrderByFromMonthDesc(studentId, sessions.current().getId());
	}

	private StudentBasics requireStudent(Long studentId) {
		return students.basics(studentId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student does not exist."));
	}

	private static String describe(FeePlan plan) {
		String text = "school " + MoneyText.of(plan.getSchoolFeePerMonth()) + " a month";
		if (plan.getDiscountPerMonth().signum() > 0) {
			text += ", discount " + MoneyText.of(plan.getDiscountPerMonth()) + " (" + plan.getDiscountReason() + ")";
		}
		return text + ", from " + plan.getFromMonth() + ", " + plan.getPayFrequency();
	}

}

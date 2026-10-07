package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.DayText;
import com.muhjain.school.student.StudentBasics;
import com.muhjain.school.student.StudentQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments and corrections (rules 9 to 13 of phase 7).
 * <ul>
 * <li>A payment is never changed or deleted. A mistake is fixed with a correction row (negative amount), by the
 * owner only.</li>
 * <li>A payment cannot be more than what is still unpaid for that head in the session. Paying future dues early is fine.</li>
 * <li>One receipt number for one visit, also when it covers school and bus.</li>
 * </ul>
 * Example: dues SCHOOL 30,000 and BUS 8,800, nothing paid. SCHOOL 7,500 + BUS 2,200 → two rows, one receipt.
 * Then SCHOOL 25,000 → 409 PAYMENT_TOO_LARGE, because only 22,500 is unpaid.
 */
@Service
public class PaymentService {

	private final FeePlanRepository plans;

	private final FeeDueRepository dues;

	private final FeePaymentRepository payments;

	private final SessionService sessions;

	private final ReceiptNumberService receipts;

	private final StudentQueryService students;

	private final AuditService audit;

	private final Clock clock;

	public PaymentService(FeePlanRepository plans, FeeDueRepository dues, FeePaymentRepository payments,
			SessionService sessions, ReceiptNumberService receipts, StudentQueryService students,
			AuditService audit, Clock clock) {
		this.plans = plans;
		this.dues = dues;
		this.payments = payments;
		this.sessions = sessions;
		this.receipts = receipts;
		this.students = students;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Records money received, in the current session.
	 *
	 * @param userId the logged-in user, from the token
	 * @throws ApiException 404 NOT_FOUND (child), 409 NO_FEE_PLAN, 400 VALIDATION (date in the future, a head twice),
	 * 409 PAYMENT_TOO_LARGE
	 */
	@Transactional
	public ReceiptResponse record(Long studentId, PaymentRequest request, Long userId) {
		requireStudent(studentId);
		LocalDate today = LocalDate.now(clock);
		LocalDate paidOn = (request.paidOn() != null) ? request.paidOn() : today;
		if (paidOn.isAfter(today)) {
			throw ApiException.validation("paidOn", "cannot be in the future");
		}
		Map<FeeHead, BigDecimal> wanted = new EnumMap<>(FeeHead.class);
		for (PaymentLine line : request.lines()) {
			if (wanted.put(line.feeHead(), line.amount()) != null) {
				throw ApiException.validation("lines", line.feeHead() + " is in the list twice");
			}
		}
		AcademicSession session = sessions.current();
		FeePlan plan = lockedPlan(studentId, session);

		// Nothing is saved before every head is checked. Example: SCHOOL is fine but BUS is too large → nothing.
		for (Map.Entry<FeeHead, BigDecimal> entry : wanted.entrySet()) {
			BigDecimal unpaid = unpaid(plan, studentId, session, entry.getKey());
			if (entry.getValue().compareTo(unpaid) > 0) {
				throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_TOO_LARGE", "Only " + MoneyText.of(unpaid)
						+ " is still unpaid for " + entry.getKey() + ". Check the amount.");
			}
		}

		String receiptNo = receipts.next(session);
		List<FeePayment> saved = new ArrayList<>();
		for (FeeHead head : FeeHead.values()) {
			BigDecimal amount = wanted.get(head);
			if (amount != null) {
				FeePayment row = payments.save(new FeePayment(studentId, session.getId(), head, amount, paidOn,
						request.mode(), receiptNo, tidy(request.note()), userId));
				saved.add(row);
				auditPayment(row, "Payment " + receiptNo + ": " + head + " " + MoneyText.of(amount) + " by "
						+ request.mode() + " on " + DayText.on(paidOn) + ".", AuditAction.CREATED);
			}
		}
		return receiptOf(receiptNo, saved, plan, studentId, session);
	}

	/**
	 * Adds a correction: a row below 0 with a note. Only for a receipt of this child and head, and not more than
	 * what that receipt still holds.
	 * Example: receipt R-2026-0412 holds SCHOOL 7,500. A correction of -500 is fine. Another of -7,500 is refused.
	 *
	 * @throws ApiException 404 NOT_FOUND (child), 409 NO_FEE_PLAN, 400 VALIDATION (amount, receipt, too much)
	 */
	@Transactional
	public ReceiptResponse correct(Long studentId, CorrectionRequest request, Long userId) {
		requireStudent(studentId);
		if (request.amount().signum() >= 0) {
			throw ApiException.validation("amount", "must be below 0 (a correction takes money back)");
		}
		AcademicSession session = sessions.current();
		FeePlan plan = lockedPlan(studentId, session);
		List<FeePayment> rows = payments.findByStudentIdAndReceiptNoAndFeeHead(studentId, request.receiptNo().strip(),
				request.feeHead())
			.stream()
			.filter(p -> p.getSessionId().equals(session.getId()))
			.toList();
		if (rows.isEmpty()) {
			throw ApiException.validation("receiptNo", "this child has no " + request.feeHead()
					+ " payment with this receipt number");
		}
		BigDecimal held = rows.stream().map(FeePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (held.add(request.amount()).signum() < 0) {
			throw ApiException.validation("amount", "the receipt holds only " + MoneyText.of(held) + " for "
					+ request.feeHead());
		}
		FeePayment original = rows.getFirst();
		FeePayment row = payments.save(new FeePayment(studentId, session.getId(), request.feeHead(),
				request.amount(), LocalDate.now(clock), original.getMode(), original.getReceiptNo(),
				request.note().strip(), userId));
		auditPayment(row, "Correction on " + row.getReceiptNo() + ": " + row.getFeeHead() + " "
				+ MoneyText.of(row.getAmount()) + ". " + row.getNote(), AuditAction.CREATED);
		return receiptOf(row.getReceiptNo(), List.of(row), plan, studentId, session);
	}

	private FeePlan lockedPlan(Long studentId, AcademicSession session) {
		return plans.lockByStudentIdAndSessionId(studentId, session.getId())
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_FEE_PLAN",
					"This child has no fee plan for " + session.getName() + ". Save the plan first."));
	}

	// Dues of the head minus money received (corrections count as negative money), never below 0.
	private BigDecimal unpaid(FeePlan plan, Long studentId, AcademicSession session, FeeHead head) {
		BigDecimal due = dues.totalOf(plan.getId(), head);
		BigDecimal paid = payments.paidFor(studentId, session.getId(), head);
		return due.subtract(paid).max(BigDecimal.ZERO);
	}

	private ReceiptResponse receiptOf(String receiptNo, List<FeePayment> rows, FeePlan plan, Long studentId,
			AcademicSession session) {
		BigDecimal total = rows.stream().map(FeePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal stillToPay = BigDecimal.ZERO;
		for (FeeHead head : FeeHead.values()) {
			stillToPay = stillToPay.add(unpaid(plan, studentId, session, head));
		}
		return new ReceiptResponse(receiptNo, total, stillToPay, rows.stream().map(PaymentResponse::of).toList());
	}

	private void auditPayment(FeePayment row, String summary, AuditAction action) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("paymentId", row.getId());
		details.put("receiptNo", row.getReceiptNo());
		details.put("feeHead", row.getFeeHead().name());
		details.put("amount", row.getAmount());
		details.put("mode", row.getMode().name());
		audit.record("STUDENT", row.getStudentId(), action, summary, details);
	}

	private StudentBasics requireStudent(Long studentId) {
		return students.basics(studentId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student does not exist."));
	}

	private static String tidy(String note) {
		return (note == null || note.isBlank()) ? null : note.strip();
	}

}

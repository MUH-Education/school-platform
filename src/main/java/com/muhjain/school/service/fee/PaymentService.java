package com.muhjain.school.service.fee;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.CorrectionRequest;
import com.muhjain.school.model.dto.request.PaymentRequest;
import com.muhjain.school.model.dto.response.PaymentLine;
import com.muhjain.school.model.dto.response.PaymentResponse;
import com.muhjain.school.model.dto.response.ReceiptResponse;
import com.muhjain.school.model.dto.response.StudentBasics;
import com.muhjain.school.model.entity.AcademicSession;
import com.muhjain.school.model.entity.FeeMonthView;
import com.muhjain.school.model.entity.FeePayment;
import com.muhjain.school.model.entity.Receipt;
import com.muhjain.school.model.enums.*;
import com.muhjain.school.model.repository.FeeMonthViewRepository;
import com.muhjain.school.model.repository.FeePaymentRepository;
import com.muhjain.school.model.repository.FeePlanRepository;
import com.muhjain.school.model.repository.ReceiptRepository;
import com.muhjain.school.service.audit.AuditService;
import com.muhjain.school.service.notification.RuleEvents;
import com.muhjain.school.service.student.GuardianService;
import com.muhjain.school.service.student.StudentQueryService;
import com.muhjain.school.util.MoneyText;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Payments and receipt cancellations (rules 9 to 13 of phase 7).
 * <ul>
 * <li>A payment line is never changed or deleted (a database trigger stops it). Cancelling a receipt adds a
 * negative line for each of its lines instead, and the months it covered are unpaid again.</li>
 * <li>Money for a head is spent on the oldest unpaid month first. A payment cannot be more than what is still
 * unpaid for that head.</li>
 * <li>One receipt number for one visit, also when it covers school and bus, also when it spans several months.</li>
 * </ul>
 * Example: dues SCHOOL 2500 and BUS 800 a month for September and October, nothing paid. SCHOOL 5000 + BUS 1600
 * → four lines (Sep and Oct, each head), one receipt.
 */
@Service
public class PaymentService {

	private final FeePlanRepository plans;

	private final FeeMonthViewRepository months;

	private final FeeDueGenerator dueGenerator;

	private final FeePaymentRepository payments;

	private final ReceiptRepository receipts;

	private final AcademicSessionService sessions;

	private final ReceiptNumberService receiptNumbers;

	private final StudentQueryService students;

	private final GuardianService guardianService;

	private final AuditService audit;

	private final RuleEvents ruleEvents;

	private final Clock clock;

	private final EntityManager entityManager;

	public PaymentService(FeePlanRepository plans, FeeMonthViewRepository months, FeeDueGenerator dueGenerator,
			FeePaymentRepository payments, ReceiptRepository receipts, AcademicSessionService sessions,
			ReceiptNumberService receiptNumbers, StudentQueryService students, GuardianService guardianService,
			AuditService audit, RuleEvents ruleEvents, Clock clock, EntityManager entityManager) {
		this.ruleEvents = ruleEvents;
		this.plans = plans;
		this.months = months;
		this.dueGenerator = dueGenerator;
		this.payments = payments;
		this.receipts = receipts;
		this.sessions = sessions;
		this.receiptNumbers = receiptNumbers;
		this.students = students;
		this.guardianService = guardianService;
		this.audit = audit;
		this.clock = clock;
		this.entityManager = entityManager;
	}

	private record MonthAllocation(LocalDate month, BigDecimal amount) {
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
		// Locked first, so two clerks cannot both pass the "not more than unpaid" check at the same moment.
		plans.lockNewestByStudentIdAndSessionId(studentId, session.getId())
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_FEE_PLAN",
					"This child has no fee plan for " + session.getName() + ". Save the plan first."));
		List<FeeMonthView> rows = months.findByStudentIdAndSessionIdOrderByMonthAsc(studentId, session.getId());

		// Nothing is saved before every head is checked. Example: SCHOOL is fine but BUS is too large → nothing.
		Map<FeeHead, List<MonthAllocation>> allocations = new EnumMap<>(FeeHead.class);
		BigDecimal total = BigDecimal.ZERO;
		for (Map.Entry<FeeHead, BigDecimal> entry : wanted.entrySet()) {
			allocations.put(entry.getKey(), allocate(rows, entry.getKey(), entry.getValue()));
			total = total.add(entry.getValue());
		}

		Long familyGuardianId = guardianService.primaryGuardianId(studentId);
		String receiptNo = receiptNumbers.next();
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		Receipt receipt = receipts.save(new Receipt(receiptNo, ReceiptKind.PAYMENT, ReceiptSource.COLLECT,
				session.getId(), familyGuardianId, paidOn, now, request.mode(), request.reference(), total, null,
				userId));
		List<FeePayment> saved = new ArrayList<>();
		allocations.forEach((head, parts) -> parts.forEach(part -> saved.add(payments
			.save(new FeePayment(receipt.getId(), studentId, session.getId(), part.month(), head, part.amount(),
					null)))));
		payments.flush();
		// The v_fee_month rows read above (line 127) are still cached by id in this session. Clear them out so
		// the balance computed below picks up the payment just flushed, instead of the pre-payment numbers.
		entityManager.clear();
		for (FeePayment row : saved) {
			auditPayment(row, "Payment " + receiptNo + ": " + row.getFeeHead() + " " + MoneyText.of(row.getAmount())
					+ " for " + row.getMonth() + " by " + request.mode() + ".", AuditAction.CREATED);
		}
		// Phase 12: the family's waiting fee reminders are skipped, once this transaction has committed.
		ruleEvents.paymentSaved(studentId, familyGuardianId);
		return receiptOf(receipt, saved, studentId, session);
	}

	/**
	 * Cancels a whole receipt, by the owner only. Every line gets a negative line that reverses it, and the
	 * months it covered become unpaid again.
	 * Example: receipt R-2026-0412 (school 2500 for September) is cancelled → one line of -2500 for September,
	 * and September is due again.
	 *
	 * @throws ApiException 404 NOT_FOUND (child or receipt), 409 ALREADY_CANCELLED
	 */
	@Transactional
	public ReceiptResponse correct(Long studentId, CorrectionRequest request, Long userId) {
		requireStudent(studentId);
		Receipt receipt = receipts.findByReceiptNo(request.receiptNo().strip())
			.orElseThrow(() -> ApiException.validation("receiptNo", "does not exist"));
		if (receipt.getStatus() == ReceiptStatus.CANCELLED) {
			throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CANCELLED", "This receipt was cancelled already.");
		}
		List<FeePayment> lines = payments.findByReceiptIdOrderById(receipt.getId());
		boolean belongsToStudent = lines.stream().anyMatch(l -> l.getStudentId().equals(studentId));
		if (!belongsToStudent) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This receipt does not belong to this child.");
		}
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		receipt.cancel(request.reason(), request.note().strip(), userId, now);
		receipts.save(receipt);
		List<FeePayment> reversals = new ArrayList<>();
		for (FeePayment line : lines) {
			reversals.add(payments.save(new FeePayment(receipt.getId(), line.getStudentId(), line.getSessionId(),
					line.getMonth(), line.getFeeHead(), line.getAmount().negate(), line.getId())));
		}
		payments.flush();
		entityManager.clear();
		// The cancelled months are unpaid again: rebuild the schedule of every child this receipt touched.
		AcademicSession session = sessions.get(receipt.getSessionId());
		Set<Long> affected = lines.stream().map(FeePayment::getStudentId).collect(Collectors.toSet());
		for (Long id : affected) {
			students.basics(id).ifPresent(s -> dueGenerator.regenerate(s, session));
		}
		auditService(receipt, "Receipt " + receipt.getReceiptNo() + " cancelled (" + request.reason() + "): "
				+ request.note(), studentId);
		return receiptOf(receipt, reversals, studentId, session);
	}

	// Spends the amount on the oldest unpaid month of this head first.
	private List<MonthAllocation> allocate(List<FeeMonthView> rows, FeeHead head, BigDecimal amount) {
		if (head == FeeHead.LATE) {
			// No due schedule for a late fee in this model yet: one line, this month.
			return List.of(new MonthAllocation(LocalDate.now(clock).withDayOfMonth(1), amount));
		}
		BigDecimal remaining = amount;
		List<MonthAllocation> result = new ArrayList<>();
		for (FeeMonthView row : rows) {
			if (remaining.signum() <= 0) {
				break;
			}
			BigDecimal open = (head == FeeHead.SCHOOL)
					? row.getSchoolAmount().subtract(row.getDiscountAmount()).subtract(row.getPaidSchool())
					: row.getBusAmount().subtract(row.getPaidBus());
			if (open.signum() <= 0) {
				continue;
			}
			BigDecimal part = remaining.min(open);
			result.add(new MonthAllocation(row.getMonth(), part));
			remaining = remaining.subtract(part);
		}
		if (remaining.signum() > 0) {
			BigDecimal unpaid = amount.subtract(remaining);
			throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_TOO_LARGE", "Only " + MoneyText.of(unpaid)
					+ " is still unpaid for " + head + ". Check the amount.");
		}
		return result;
	}

	private ReceiptResponse receiptOf(Receipt receipt, List<FeePayment> lines, Long studentId,
			AcademicSession session) {
		BigDecimal total = lines.stream().map(FeePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal stillToPay = months.findByStudentIdAndSessionIdOrderByMonthAsc(studentId, session.getId())
			.stream()
			.map(FeeMonthView::getBalance)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		return ReceiptResponse.of(receipt, total, stillToPay, lines.stream().map(PaymentResponse::of).toList());
	}

	private void auditPayment(FeePayment row, String summary, AuditAction action) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("paymentId", row.getId());
		details.put("receiptId", row.getReceiptId());
		details.put("feeHead", row.getFeeHead().name());
		details.put("amount", row.getAmount());
		details.put("month", row.getMonth().toString());
		audit.record("STUDENT", row.getStudentId(), action, summary, details);
	}

	private void auditService(Receipt receipt, String summary, Long studentId) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("receiptId", receipt.getId());
		details.put("receiptNo", receipt.getReceiptNo());
		audit.record("STUDENT", studentId, AuditAction.UPDATED, summary, details);
	}

	private StudentBasics requireStudent(Long studentId) {
		return students.basics(studentId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student does not exist."));
	}

}

package com.muhjain.school.service.fee;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.response.PageResponse;
import com.muhjain.school.model.dto.response.StudentBasics;
import com.muhjain.school.model.entity.Receipt;
import com.muhjain.school.model.repository.ReceiptRepository;
import com.muhjain.school.service.student.StudentQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.muhjain.school.model.dto.response.PaymentListItem;
import com.muhjain.school.model.entity.FeePayment;
import com.muhjain.school.model.repository.FeePaymentRepository;

/**
 * The payment list (rule "GET /payments with date filter and paging"). Newest first.
 * Example: {@code from = 2026-10-01, to = 2026-10-07} → every payment and correction whose receipt was made
 * on those days.
 */
@Service
public class PaymentQueryService {

	static final int MAX_PAGE_SIZE = 100;

	private final FeePaymentRepository payments;

	private final ReceiptRepository receipts;

	private final StudentQueryService students;

	private final Clock clock;

	public PaymentQueryService(FeePaymentRepository payments, ReceiptRepository receipts,
			StudentQueryService students, Clock clock) {
		this.payments = payments;
		this.receipts = receipts;
		this.students = students;
		this.clock = clock;
	}

	/**
	 * @param from first day, inclusive, or null
	 * @param to last day, inclusive, or null
	 * @param sessionId only this session, or null for all
	 * @throws ApiException 400 VALIDATION (page, size, dates)
	 */
	@Transactional(readOnly = true)
	public PageResponse<PaymentListItem> list(LocalDate from, LocalDate to, Long sessionId, int page, int size) {
		if (page < 0) {
			throw ApiException.validation("page", "must be 0 or more");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw ApiException.validation("size", "must be between 1 and " + MAX_PAGE_SIZE);
		}
		if (from != null && to != null && from.isAfter(to)) {
			throw ApiException.validation("from", "must not be after the end date");
		}
		LocalDate fromDay = (from != null) ? from : LocalDate.EPOCH;
		LocalDate toDay = (to != null) ? to : LocalDate.now(clock);
		Page<FeePayment> found = payments.search(fromDay, toDay, sessionId, PageRequest.of(page, size));
		Set<Long> studentIds = found.getContent().stream().map(FeePayment::getStudentId).collect(Collectors.toSet());
		Set<Long> receiptIds = found.getContent().stream().map(FeePayment::getReceiptId).collect(Collectors.toSet());
		Map<Long, StudentBasics> byStudent = students.basics(studentIds);
		Map<Long, Receipt> byReceipt = receipts.findAllById(receiptIds)
			.stream()
			.collect(Collectors.toMap(Receipt::getId, r -> r));
		List<PaymentListItem> items = found.getContent().stream().map(p -> {
			StudentBasics s = byStudent.get(p.getStudentId());
			Receipt r = byReceipt.get(p.getReceiptId());
			return new PaymentListItem(p.getId(), (r != null) ? r.getReceiptNo() : null, p.getStudentId(),
					(s != null) ? s.name() : null, (s != null) ? s.admissionNo() : null,
					(s != null) ? s.className() : null, p.getMonth(), p.getFeeHead(), p.getAmount(),
					(r != null) ? r.getPaidOn() : null, (r != null) ? r.getMode() : null);
		}).toList();
		return new PageResponse<>(items, found.getNumber(), found.getSize(), found.getTotalElements(),
				found.getTotalPages());
	}

}

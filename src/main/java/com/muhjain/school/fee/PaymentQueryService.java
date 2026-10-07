package com.muhjain.school.fee;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.PageResponse;
import com.muhjain.school.student.StudentBasics;
import com.muhjain.school.student.StudentQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The payment list (rule "GET /payments with date filter and paging"). Newest first.
 * Example: {@code from = 2026-10-01, to = 2026-10-07} → every payment and correction typed on those days.
 */
@Service
public class PaymentQueryService {

	static final int MAX_PAGE_SIZE = 100;

	private final FeePaymentRepository payments;

	private final StudentQueryService students;

	public PaymentQueryService(FeePaymentRepository payments, StudentQueryService students) {
		this.payments = payments;
		this.students = students;
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
		Specification<FeePayment> filter = (root, query, cb) -> cb.conjunction();
		if (from != null) {
			filter = filter.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("paidOn"), from));
		}
		if (to != null) {
			filter = filter.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("paidOn"), to));
		}
		if (sessionId != null) {
			filter = filter.and((root, query, cb) -> cb.equal(root.get("sessionId"), sessionId));
		}
		Page<FeePayment> found = payments.findAll(filter, PageRequest.of(page, size,
				Sort.by(Sort.Order.desc("paidOn"), Sort.Order.desc("id"))));
		Set<Long> ids = found.getContent().stream().map(FeePayment::getStudentId).collect(Collectors.toSet());
		Map<Long, StudentBasics> byId = students.basics(ids);
		List<PaymentListItem> items = found.getContent().stream().map(p -> {
			StudentBasics s = byId.get(p.getStudentId());
			return new PaymentListItem(p.getId(), p.getReceiptNo(), p.getStudentId(), (s != null) ? s.name() : null,
					(s != null) ? s.admissionNo() : null, (s != null) ? s.className() : null, p.getFeeHead(),
					p.getAmount(), p.getPaidOn(), p.getMode(), p.getNote());
		}).toList();
		return new PageResponse<>(items, found.getNumber(), found.getSize(), found.getTotalElements(),
				found.getTotalPages());
	}

}

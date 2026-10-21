package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.entity.FeePayment;
import com.muhjain.school.model.enums.FeeHead;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One line of a receipt. Example: SCHOOL, September 2026, ₹2,500. A reversal has a negative amount. */
public record PaymentResponse(Long id, Long studentId, Long receiptId, LocalDate month, FeeHead feeHead,
		BigDecimal amount, Long reversalOfId) {

	public static PaymentResponse of(FeePayment payment) {
		return new PaymentResponse(payment.getId(), payment.getStudentId(), payment.getReceiptId(),
				payment.getMonth(), payment.getFeeHead(), payment.getAmount(), payment.getReversalOfId());
	}

}

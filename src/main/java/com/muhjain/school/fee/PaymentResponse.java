package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One payment row. Example: SCHOOL ₹7,500 by UPI on 1 Apr, receipt R-2026-0412. A correction has a negative amount. */
public record PaymentResponse(Long id, Long studentId, String receiptNo, FeeHead feeHead, BigDecimal amount,
		LocalDate paidOn, PaymentMode mode, String note) {

	static PaymentResponse of(FeePayment payment) {
		return new PaymentResponse(payment.getId(), payment.getStudentId(), payment.getReceiptNo(),
				payment.getFeeHead(), payment.getAmount(), payment.getPaidOn(), payment.getMode(),
				payment.getNote());
	}

}

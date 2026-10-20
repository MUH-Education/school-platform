package com.muhjain.school.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.muhjain.school.model.entity.FeePayment;
import com.muhjain.school.model.enums.FeeHead;
import com.muhjain.school.model.enums.PaymentMode;

/** One payment row. Example: SCHOOL ₹7,500 by UPI on 1 Apr, receipt R-2026-0412. A correction has a negative amount. */
public record PaymentResponse(Long id, Long studentId, String receiptNo, FeeHead feeHead, BigDecimal amount,
		LocalDate paidOn, PaymentMode mode, String note) {

	public static PaymentResponse of(FeePayment payment) {
	return new PaymentResponse(payment.getId(), payment.getStudentId(), payment.getReceiptNo(),
				payment.getFeeHead(), payment.getAmount(), payment.getPaidOn(), payment.getMode(),
				payment.getNote());
	}

}

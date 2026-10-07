package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the payment list, with the child's name. A correction has a negative amount.
 * Example: R-2026-0412, Aryan Jain (A-2026-118, class 3), SCHOOL ₹7,500 by UPI on 1 Apr.
 */
public record PaymentListItem(Long id, String receiptNo, Long studentId, String studentName, String admissionNo,
		String className, FeeHead feeHead, BigDecimal amount, LocalDate paidOn, PaymentMode mode, String note) {

}

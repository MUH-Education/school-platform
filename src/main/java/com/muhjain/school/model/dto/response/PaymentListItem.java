package com.muhjain.school.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.muhjain.school.model.enums.FeeHead;
import com.muhjain.school.model.enums.PaymentMode;

/**
 * One row of the payment list, with the child's name and the receipt it belongs to. A reversal has a negative
 * amount.
 * Example: R-2026-0412, Aryan Jain (A-2026-118, class 3), SCHOOL ₹2,500 for September, by UPI on 1 Sep.
 */
public record PaymentListItem(Long id, String receiptNo, Long studentId, String studentName, String admissionNo,
		String className, LocalDate month, FeeHead feeHead, BigDecimal amount, LocalDate paidOn, PaymentMode mode) {

}

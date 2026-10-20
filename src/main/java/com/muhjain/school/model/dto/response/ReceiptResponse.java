package com.muhjain.school.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.muhjain.school.model.entity.Receipt;
import com.muhjain.school.model.enums.PaymentMode;

/**
 * The answer after a payment or a correction. {@code total} is the sum of the lines. {@code stillToPay} is
 * what the child still owes in this session, all dues, also those not yet due.
 * Example: school 2500 and bus 800 a month, the family pays 3300 → total 3300, stillToPay for the rest of the year.
 */
public record ReceiptResponse(String receiptNo, LocalDate paidOn, PaymentMode mode, BigDecimal total,
		BigDecimal stillToPay, List<PaymentResponse> payments) {

	public static ReceiptResponse of(Receipt receipt, BigDecimal total, BigDecimal stillToPay,
			List<PaymentResponse> payments) {
		return new ReceiptResponse(receipt.getReceiptNo(), receipt.getPaidOn(), receipt.getMode(), total,
				stillToPay, payments);
	}

}

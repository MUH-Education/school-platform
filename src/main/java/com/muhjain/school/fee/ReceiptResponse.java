package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.util.List;

/**
 * The answer after a payment or a correction. {@code total} is the sum of the rows. {@code stillToPay} is what the
 * family still owes in this session, all dues, also those not yet due.
 * Example: school ₹30,000 and bus ₹8,800, the family pays ₹9,700 → total 9700, stillToPay 29100.
 */
public record ReceiptResponse(String receiptNo, BigDecimal total, BigDecimal stillToPay,
		List<PaymentResponse> payments) {

}

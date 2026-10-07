package com.muhjain.school.fee;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A correction of a wrong payment, by the owner only. It is a new row with a negative amount. The payment itself is
 * never changed. The row gets the same receipt number, the same payment mode and today's date.
 * Example: the clerk typed ₹7,500 but the family paid ₹7,000:
 * {@code {"receiptNo":"R-2026-0412","feeHead":"SCHOOL","amount":-500,"note":"Typed 7500, paid 7000"}}
 */
public record CorrectionRequest(@NotBlank String receiptNo, @NotNull FeeHead feeHead,
		@NotNull @Digits(integer = 10, fraction = 2) BigDecimal amount, @NotBlank @Size(max = 200) String note) {

}

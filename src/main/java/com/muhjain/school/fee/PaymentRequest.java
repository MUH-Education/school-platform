package com.muhjain.school.fee;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Money the office received. One receipt may cover both heads: then it has two lines and both rows get the same
 * receipt number. The system only records "received by UPI". It never takes card numbers or a UPI PIN.
 * <ul>
 * <li>{@code paidOn}: optional, means today. It cannot be in the future.</li>
 * <li>{@code mode}: CASH, UPI, BANK_TRANSFER or CHEQUE.</li>
 * </ul>
 * Example: {@code {"mode":"UPI","lines":[{"feeHead":"SCHOOL","amount":7500},{"feeHead":"BUS","amount":2200}]}}
 */
public record PaymentRequest(LocalDate paidOn, @NotNull PaymentMode mode, @Size(max = 200) String note,
		@NotEmpty @Valid List<@NotNull @Valid PaymentLine> lines) {

}

package com.muhjain.school.model.dto.request;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.muhjain.school.model.enums.PaymentMode;
import com.muhjain.school.model.dto.response.PaymentLine;

/**
 * Money the office received. One receipt may cover both heads: then it has two lines. Money for a head is
 * spent on the oldest unpaid month first. The system only records "received by UPI". It never takes card
 * numbers or a UPI PIN.
 * <ul>
 * <li>{@code paidOn}: optional, means today. It cannot be in the future.</li>
 * <li>{@code mode}: CASH, UPI, BANK_TRANSFER or CHEQUE.</li>
 * <li>{@code reference}: the UPI reference or cheque number, optional.</li>
 * </ul>
 * Example: {@code {"mode":"UPI","lines":[{"feeHead":"SCHOOL","amount":2500},{"feeHead":"BUS","amount":800}]}}
 */
@Schema(example = "{\"mode\": \"UPI\", \"lines\": [{\"feeHead\": \"SCHOOL\", \"amount\": 2500}, {\"feeHead\": \"BUS\", \"amount\": 800}]}")
public record PaymentRequest(LocalDate paidOn, @NotNull PaymentMode mode, @Size(max = 60) String reference,
		@NotEmpty @Valid List<@NotNull @Valid PaymentLine> lines) {

}

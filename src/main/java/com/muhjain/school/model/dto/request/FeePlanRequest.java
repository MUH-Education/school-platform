package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.DiscountReason;
import com.muhjain.school.model.enums.PayFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Create or change the fee plan of the current session. Money is in rupees, a month (the bus fee is not
 * here any more: it lives on the child's bus row, and can change with the route).
 * <ul>
 * <li>{@code schoolFee}: a month.</li>
 * <li>{@code discount}: taken off the school fee, a month. A discount above 0 needs a {@code discountReason}
 * other than NONE.</li>
 * </ul>
 * A change starts a new time slice from the first day of the current month; the months already paid keep
 * their old amount.
 * Example: {@code {"schoolFee":2500,"discount":0,"payFrequency":"MONTHLY"}}
 */
@Schema(example = "{\"schoolFee\": 2500, \"discount\": 0, \"payFrequency\": \"MONTHLY\"}")
public record FeePlanRequest(
		@NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal schoolFee,
		@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal discount, DiscountReason discountReason,
		@NotNull PayFrequency payFrequency) {

}

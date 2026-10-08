package com.muhjain.school.fee;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Create or change the fee plan of the current session. Money is in rupees.
 * <ul>
 * <li>{@code schoolFee}: for the year (or for the rest of the year for a child who joins late).</li>
 * <li>{@code busFee}: for the year, empty or 0 for no bus.</li>
 * <li>{@code discount}: taken off the school fee. A discount above 0 needs a {@code discountReason} other than NONE.</li>
 * </ul>
 * Example: {@code {"schoolFee":30000,"busFee":8800,"discount":0,"payFrequency":"QUARTERLY"}}
 */
@Schema(example = "{\"schoolFee\": 30000, \"busFee\": 8800, \"discount\": 0, \"payFrequency\": \"QUARTERLY\"}")
public record FeePlanRequest(
		@NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal schoolFee,
		@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal busFee,
		@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal discount, DiscountReason discountReason,
		@NotNull PayFrequency payFrequency) {

}

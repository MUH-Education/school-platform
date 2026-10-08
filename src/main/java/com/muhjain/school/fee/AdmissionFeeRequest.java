package com.muhjain.school.fee;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * The fee part of an admission (part 4 "Fees" of screen 6).
 * <ul>
 * <li>{@code schoolFee}: optional. Empty means the standard fee of the class in the current session
 * (400 if the office has not set it).</li>
 * <li>{@code busFee}: optional. Empty means the {@code busFee} of the {@code bus} part, or 0 for no bus.</li>
 * <li>{@code discount} and {@code discountReason}: as in {@link FeePlanRequest}.</li>
 * </ul>
 * Example: {@code {"schoolFee":30000,"busFee":8800,"payFrequency":"QUARTERLY"}}
 */
@Schema(example = "{\"schoolFee\": 30000, \"busFee\": 8800, \"payFrequency\": \"QUARTERLY\"}")
public record AdmissionFeeRequest(@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal schoolFee,
		@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal busFee,
		@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal discount, DiscountReason discountReason,
		@NotNull PayFrequency payFrequency) {

}

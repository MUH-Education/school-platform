package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.DiscountReason;
import com.muhjain.school.model.enums.PayFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * The fee part of an admission (part 4 "Fees" of screen 6). The bus fee is not here: it is on the bus part of
 * the admission.
 * <ul>
 * <li>{@code schoolFee}: a month, optional. Empty means the standard fee of the class in the current session.</li>
 * <li>{@code discount} and {@code discountReason}: as in {@link FeePlanRequest}.</li>
 * </ul>
 * Example: {@code {"schoolFee":2500,"payFrequency":"MONTHLY"}}
 */
@Schema(example = "{\"schoolFee\": 2500, \"payFrequency\": \"MONTHLY\"}")
public record AdmissionFeeRequest(@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal schoolFee,
		@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal discount, DiscountReason discountReason,
		@NotNull PayFrequency payFrequency) {

}

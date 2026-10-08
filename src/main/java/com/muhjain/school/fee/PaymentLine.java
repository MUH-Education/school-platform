package com.muhjain.school.fee;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** Money for one fee head. Example: {@code {"feeHead":"SCHOOL","amount":7500}} */
@Schema(example = "{\"feeHead\": \"SCHOOL\", \"amount\": 7500}")
public record PaymentLine(@NotNull FeeHead feeHead,
		@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal amount) {

}

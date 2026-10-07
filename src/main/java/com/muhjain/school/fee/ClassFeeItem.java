package com.muhjain.school.fee;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The school fee of one class for the year. Example: {@code {"className":"3","schoolFee":30000}} */
public record ClassFeeItem(@NotBlank String className, @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal schoolFee) {

}

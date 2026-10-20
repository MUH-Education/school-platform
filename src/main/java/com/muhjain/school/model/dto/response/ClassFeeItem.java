package com.muhjain.school.model.dto.response;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The standard school fee of one class, a month. Example: {@code {"className":"3","perMonth":2500}} */
public record ClassFeeItem(@NotBlank String className, @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal perMonth) {

}

package com.muhjain.school.model.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Set what one employee is paid in a month. Money is always {@code BigDecimal}, never a {@code double}.
 * Example: {@code { "monthlySalary": 18500.00 }}
 */
public record SetSalaryRequest(
		@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal monthlySalary) {

}

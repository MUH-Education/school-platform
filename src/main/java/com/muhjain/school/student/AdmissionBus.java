package com.muhjain.school.student;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * The bus part of an admission. Leave the whole {@code bus} object out for a child with no bus.
 * {@code fromDate} is optional and means "from the joining day".
 * Example: {@code { "routeId": 4, "stopId": 18, "fromDate": "2026-10-07", "busFee": 8800 }}
 */
public record AdmissionBus(@NotNull Long routeId, @NotNull Long stopId, LocalDate fromDate,
		@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal busFee) {

}

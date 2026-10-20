package com.muhjain.school.model.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.muhjain.school.model.enums.TransportReason;

/**
 * Start the bus, change the route or stop, or stop the bus. One body for all three. {@code reason} and
 * {@code note} are optional and say why.
 * <ul>
 * <li>Start or change: {@code { "usesBus": true, "routeId": 9, "stopId": 44, "fromDate": "2026-11-02",
 * "busFee": 4000 }}</li>
 * <li>Stop: {@code { "usesBus": false, "fromDate": "2026-12-01" }} (the last day on the bus is 30 Nov)</li>
 * </ul>
 */
public record TransportRequest(@NotNull Boolean usesBus, Long routeId, Long stopId, @NotNull LocalDate fromDate,
		@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal busFee, TransportReason reason,
		@Size(max = 200) String note) {

}

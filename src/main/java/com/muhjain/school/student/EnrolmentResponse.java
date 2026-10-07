package com.muhjain.school.student;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of a child's bus history. {@code toDate} null means "still going on".
 * Example: Route 4, Jakhal, from 1 Apr 2026 to 30 Nov 2026, fee 8800.
 */
public record EnrolmentResponse(Long id, Long routeId, String routeName, Long stopId, String stopName,
		LocalDate fromDate, LocalDate toDate, BigDecimal busFee, boolean open) {

}

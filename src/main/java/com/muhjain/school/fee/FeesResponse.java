package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * "Fees this year" of one child (screen 10).
 * <ul>
 * <li>{@code plan}: null when the child has no plan yet. Then the lists are empty, the totals are 0 and
 * {@code status} is null.</li>
 * <li>{@code pendingNow}: dues up to {@code asOf} that are not paid. Example on 7 Oct: ₹7,500.</li>
 * <li>{@code remainingThisYear}: every due of the session that is not paid. Example: ₹15,000.</li>
 * <li>{@code nextDueOn} and {@code nextDueAmount}: the next day after today with money still to come (both heads
 * together), or null.</li>
 * <li>{@code heads}: SCHOOL then BUS. {@code status}: the worse of the two.</li>
 * </ul>
 */
public record FeesResponse(Long studentId, Long sessionId, String sessionName, LocalDate asOf, FeePlanSummary plan,
		List<DueView> dues, List<PaymentResponse> payments, List<HeadView> heads, BigDecimal pendingNow,
		BigDecimal remainingThisYear, LocalDate nextDueOn, BigDecimal nextDueAmount, FeeStatus status) {

}

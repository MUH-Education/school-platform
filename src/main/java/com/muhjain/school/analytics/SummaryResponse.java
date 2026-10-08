package com.muhjain.school.analytics;

import java.math.BigDecimal;

/**
 * The cards on top of the Analytics screen, for one filter.
 * <ul>
 * <li>{@code busPercent}: children on a bus today ÷ all children.</li>
 * <li>{@code schoolFeeCollectedPercent}, {@code busFeeCollectedPercent}: money that covers the dues up to today ÷
 * those dues. Nothing due → 0.0.</li>
 * <li>{@code studentsWithPending}: children with a due up to today that is not paid.</li>
 * </ul>
 * Example: {@code {"students":12,"studentsOnBus":5,"busPercent":41.7,"schoolFeeCollectedPercent":79.2,
 * "busFeeCollectedPercent":80.0,"studentsWithPending":6}}
 */
public record SummaryResponse(Long sessionId, String sessionName, int students, int studentsOnBus,
		BigDecimal busPercent, BigDecimal schoolFeeCollectedPercent, BigDecimal busFeeCollectedPercent,
		int studentsWithPending) {

}

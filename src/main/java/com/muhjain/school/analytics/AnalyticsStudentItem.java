package com.muhjain.school.analytics;

import java.math.BigDecimal;

import com.muhjain.school.fee.FeeStatus;
import com.muhjain.school.student.FatherOccupation;

/**
 * One row of the Analytics list and of the CSV file (rule 9). There is no phone number on purpose.
 * <ul>
 * <li>{@code busRoute}: like "Route 4", null with no bus today.</li>
 * <li>{@code schoolFeeStatus}, {@code busFeeStatus}: ON_TIME, DELAYED or DEFAULTED. Null when the child has no fee
 * plan, and null for the bus when the plan has no bus dues. {@code feeStatus} is the worse of the two.</li>
 * <li>{@code pendingAmount}: dues up to today that are not paid, both heads. 0 with no plan.</li>
 * </ul>
 * Example: Aryan, 3, Jakhal, FARMER_SMALL, Route 4, ON_TIME, DELAYED, DELAYED, 2200.00.
 */
public record AnalyticsStudentItem(Long id, String name, String className, String village,
		FatherOccupation fatherOccupation, String busRoute, FeeStatus schoolFeeStatus, FeeStatus busFeeStatus,
		FeeStatus feeStatus, BigDecimal pendingAmount) {

}

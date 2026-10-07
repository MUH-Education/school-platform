package com.muhjain.school.student;

import com.muhjain.school.fee.FeeStatus;

/**
 * One row of the Students list (rule 23). {@code busNow} is like "Route 4 · Jakhal", null if no bus today.
 * {@code parentPhone} is the first (primary) phone. {@code feeStatus} (ON_TIME, DELAYED or DEFAULTED) is the "Fee"
 * column: null when the child has no fee plan this session, and also null for a user without FEES_VIEW.
 * Example: Aryan, A-2026-118, class 3, Jakhal, Route 4 · Jakhal, ON_TIME.
 */
public record StudentListItem(Long id, String name, String admissionNo, String className, String section,
		String village, String busNow, String parentPhone, boolean hasPhoto, FeeStatus feeStatus) {

}

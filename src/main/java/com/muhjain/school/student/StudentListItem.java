package com.muhjain.school.student;

/**
 * One row of the Students list (rule 23). {@code busNow} is like "Route 4 · Jakhal", null if no bus today.
 * {@code parentPhone} is the first (primary) phone. Example: Aryan, A-2026-118, class 3, Jakhal, Route 4 · Jakhal.
 */
public record StudentListItem(Long id, String name, String admissionNo, String className, String section,
		String village, String busNow, String parentPhone, boolean hasPhoto) {

}

package com.muhjain.school.student;

/**
 * One child on a route on one day. Phase 4 (the manifest) and Phase 5 (the SMS wording) read this.
 * Example: {@code RouteChild(118, "Aryan", "A-2026-118", M, "3", "B", 18)} boards at stop 18.
 */
public record RouteChild(Long studentId, String name, String admissionNo, Gender gender, String className,
		String section, Long stopId) {

}

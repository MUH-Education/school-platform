package com.muhjain.school.student;

import java.time.LocalDate;

/**
 * The few facts about a child that fees need. Example: Aryan, class 3, joined 1 Apr 2026, ACTIVE.
 * Fees ask for this instead of the whole student, so they do not depend on the student screens.
 */
public record StudentBasics(Long id, String admissionNo, String name, String className, LocalDate joinedOn,
		StudentStatus status) {

}

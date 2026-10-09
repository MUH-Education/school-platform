package com.muhjain.school.staff;

/**
 * What an employee of the school is. The first three are also the duties on a vehicle: a DRIVER duty needs a
 * DRIVER (rule 11). TEACHER has no duty in {@link Duty}, so a teacher can never be put on a bus.
 * Question C6 in docs/08-decisions.md was answered on 9 Oct 2026: TEACHER was added.
 */
public enum StaffType {

	DRIVER, ATTENDANT, HELPER, TEACHER

}

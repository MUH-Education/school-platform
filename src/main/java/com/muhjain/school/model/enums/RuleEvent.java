package com.muhjain.school.model.enums;

/**
 * Something that happened in the school and can fire an EVENT rule.
 * Example: ADMISSION_SAVED for student 129 → the WELCOME rule, next morning.
 * PAYMENT_SAVED does not make a message; it only skips a waiting fee reminder of that family at once.
 */
public enum RuleEvent {

	ADMISSION_SAVED, BUS_LATE, NO_MORNING_TAP, PAYMENT_SAVED

}

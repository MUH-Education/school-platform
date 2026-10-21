package com.muhjain.school.model.enums;

/**
 * What kind of message the office sends. Each kind has one {@code KindPolicy} class (who may send, which audiences,
 * stop list, quiet hours). To add a kind: one value here, one policy class, one Flyway {@code check} change.
 * Example: MARKETING "Open day on Sunday", FEE_REMINDER "Fee for 2 months is pending".
 */
public enum NotificationKind {

	NOTICE, URGENT, MARKETING, GREETING, FEE_REMINDER, ONE_FAMILY, STAFF

}

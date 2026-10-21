package com.muhjain.school.model.enums;

/**
 * Why a recipient guard did not let a message go. Saved on the outbox row ({@code skip_reason}, status SKIPPED),
 * so the report shows "5 already paid, 2 promised a date". The database has no list of these on purpose:
 * a new guard is one Java class, not a migration.
 */
public enum SkipReason {

	NO_PHONE, DUPLICATE, PERSON_INACTIVE, STOPPED, WEEKLY_CAP, TEMPLATE_NOT_APPROVED, OUTSIDE_WINDOW,
	ALREADY_PAID, PROMISED, REMINDED_RECENTLY, CREDIT_COVERS, BELOW_FILTER,
	NOT_ON_ROUTE_TODAY, FILTER_NO_LONGER_MATCHES, NOW_ADMITTED, TOO_OLD

}

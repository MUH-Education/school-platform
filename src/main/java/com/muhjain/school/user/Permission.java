package com.muhjain.school.user;

/**
 * One small thing a person may do. Controllers check these, never roles.
 * Example: {@code @PreAuthorize("hasAuthority('VEHICLES_VIEW')")}.
 * The order is the order of the table in docs/05-roles-permissions.md.
 */
public enum Permission {

	BUS_STATUS_VIEW,
	TRIPS_RECORD,
	TRIPS_RECORD_ANY,
	ROUTES_VIEW,
	ROUTES_EDIT,
	VEHICLES_VIEW,
	VEHICLES_EDIT,
	STUDENTS_VIEW,
	STUDENTS_EDIT,
	ADMISSIONS_CREATE,
	MESSAGES_VIEW,
	ENQUIRIES_VIEW,
	ENQUIRIES_EDIT,
	FEES_VIEW,
	FEES_EDIT,
	FEES_CORRECT,
	ANALYTICS_VIEW,
	USERS_MANAGE,
	SETTINGS_EDIT

}

package com.muhjain.school.model.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static com.muhjain.school.model.enums.Permission.*;

/**
 * A job title. Each role is a fixed bundle of permissions (docs/05-roles-permissions.md).
 * Example: ATTENDANT has only TRIPS_RECORD.
 */
public enum Role {

	OWNER(EnumSet.allOf(Permission.class)),

	OFFICE_ADMIN(EnumSet.of(BUS_STATUS_VIEW, TRIPS_RECORD_ANY, ROUTES_VIEW, VEHICLES_VIEW, STUDENTS_VIEW,
			STUDENTS_EDIT, ADMISSIONS_CREATE, MESSAGES_VIEW, ENQUIRIES_VIEW, ENQUIRIES_EDIT, FEES_VIEW, FEES_EDIT,
			ANALYTICS_VIEW)),

	TRANSPORT_INCHARGE(EnumSet.of(BUS_STATUS_VIEW, TRIPS_RECORD, TRIPS_RECORD_ANY, ROUTES_VIEW, ROUTES_EDIT,
			VEHICLES_VIEW, VEHICLES_EDIT, STUDENTS_VIEW, MESSAGES_VIEW)),

	ADMISSIONS_DESK(EnumSet.of(STUDENTS_VIEW, ADMISSIONS_CREATE, ENQUIRIES_VIEW, ENQUIRIES_EDIT, FEES_VIEW,
			FEES_EDIT, ANALYTICS_VIEW)),

	ATTENDANT(EnumSet.of(TRIPS_RECORD));

	private final Set<Permission> permissions;

	Role(EnumSet<Permission> permissions) {
		this.permissions = Collections.unmodifiableSet(permissions);
	}

	/** In the order of {@link Permission}. */
	public Set<Permission> permissions() {
		return permissions;
	}

	public boolean has(Permission permission) {
		return permissions.contains(permission);
	}

}

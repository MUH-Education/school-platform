package com.muhjain.school.user;

import java.util.List;

/**
 * The table "Which role has which permission", for the Users and roles screen.
 * Example: {@code { "permissions": ["BUS_STATUS_VIEW", ...],
 * "roles": [ { "role": "ATTENDANT", "permissions": ["TRIPS_RECORD"] }, ... ] }}
 */
public record RolesResponse(List<Permission> permissions, List<RoleRow> roles) {

	public record RoleRow(Role role, List<Permission> permissions) {

	}

}

package com.muhjain.school.model.dto.response;

import java.util.List;
import com.muhjain.school.model.enums.Permission;
import com.muhjain.school.model.enums.Role;

/**
 * The table "Which role has which permission", for the Users and roles screen.
 * Example: {@code { "permissions": ["BUS_STATUS_VIEW", ...],
 * "roles": [ { "role": "ATTENDANT", "permissions": ["TRIPS_RECORD"] }, ... ] }}
 */
public record RolesResponse(List<Permission> permissions, List<RoleRow> roles) {

	public record RoleRow(Role role, List<Permission> permissions) {

	}

}

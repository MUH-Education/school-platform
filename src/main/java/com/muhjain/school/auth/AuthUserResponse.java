package com.muhjain.school.auth;

import java.util.List;

import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Permission;

/**
 * Who is logged in. Used by the login answer and by {@code GET /api/v1/auth/me}.
 * Example: {@code { "id": 2, "name": "Neelam", "phone": "+919812340002", "role": "OFFICE_ADMIN",
 * "permissions": ["BUS_STATUS_VIEW", ...], "route": null }}
 *
 * @param route the attendant's route today. Always null until Phase 4 builds routes and trips.
 */
public record AuthUserResponse(Long id, String name, String phone, String role, List<String> permissions,
		RouteInfo route) {

	/** Example: {@code { "id": 4, "name": "Route 4", "vehicle": "Van 4" }} */
	public record RouteInfo(Long id, String name, String vehicle) {

	}

	public static AuthUserResponse of(AppUser user) {
		List<String> permissions = user.getRole().permissions().stream().map(Permission::name).toList();
		return new AuthUserResponse(user.getId(), user.getName(), user.getPhone(), user.getRole().name(), permissions,
				null);
	}

}

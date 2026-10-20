package com.muhjain.school.model.dto.response;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.LoginClient;
import com.muhjain.school.model.enums.Role;

/**
 * One row of the Users and roles screen.
 * Example: {@code { "id": 2, "phone": "+919812340002", "name": "Neelam", "role": "OFFICE_ADMIN", "staffId": null,
 * "active": true, "lastLoginAt": "2026-10-07T09:15:00+05:30", "lastLoginClient": "WEB", "createdAt": "...",
 * "createdBy": 1 }}
 */
public record UserResponse(Long id, String phone, String name, Role role, Long staffId, boolean active,
		OffsetDateTime lastLoginAt, LoginClient lastLoginClient, OffsetDateTime createdAt, Long createdBy) {

	public static UserResponse of(AppUser user, ZoneId zone) {
	return new UserResponse(user.getId(), user.getPhone(), user.getName(), user.getRole(), user.getStaffId(),
				user.isActive(), at(user.getLastLoginAt(), zone), user.getLastLoginClient(), at(user.getCreatedAt(), zone),
				user.getCreatedBy());
	}

	private static OffsetDateTime at(Instant instant, ZoneId zone) {
		return (instant != null) ? instant.atZone(zone).toOffsetDateTime() : null;
	}

}

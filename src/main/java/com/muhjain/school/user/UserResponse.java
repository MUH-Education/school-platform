package com.muhjain.school.user;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * One row of the Users and roles screen.
 * Example: {@code { "id": 2, "phone": "+919812340002", "name": "Neelam", "role": "OFFICE_ADMIN", "staffId": null,
 * "active": true, "lastLoginAt": "2026-10-07T09:15:00+05:30", "createdAt": "...", "createdBy": 1 }}
 */
public record UserResponse(Long id, String phone, String name, Role role, Long staffId, boolean active,
		OffsetDateTime lastLoginAt, OffsetDateTime createdAt, Long createdBy) {

	static UserResponse of(AppUser user, ZoneId zone) {
		return new UserResponse(user.getId(), user.getPhone(), user.getName(), user.getRole(), user.getStaffId(),
				user.isActive(), at(user.getLastLoginAt(), zone), at(user.getCreatedAt(), zone), user.getCreatedBy());
	}

	private static OffsetDateTime at(Instant instant, ZoneId zone) {
		return (instant != null) ? instant.atZone(zone).toOffsetDateTime() : null;
	}

}

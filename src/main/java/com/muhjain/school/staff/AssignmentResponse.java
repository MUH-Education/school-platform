package com.muhjain.school.staff;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * One row of "who worked on this vehicle". {@code toDate} null means "still going on".
 * Example: {@code { "id": 31, "vehicleId": 4, "staffId": 21, "staffName": "Surender", "duty": "DRIVER",
 * "fromDate": "2026-10-12", "toDate": "2026-10-16", "temporary": true, "reason": "ON_LEAVE", "createdBy": 2,
 * "createdAt": "2026-10-07T09:15:00+05:30" }}
 */
public record AssignmentResponse(Long id, Long vehicleId, Long staffId, String staffName, Duty duty,
		LocalDate fromDate, LocalDate toDate, boolean temporary, ChangeReason reason, Long createdBy,
		OffsetDateTime createdAt) {

	static AssignmentResponse of(VehicleAssignment row, String staffName, ZoneId zone) {
		return new AssignmentResponse(row.getId(), row.getVehicleId(), row.getStaffId(), staffName, row.getDuty(),
				row.getFromDate(), row.getToDate(), row.isTemporary(), row.getReason(), row.getCreatedBy(),
				row.getCreatedAt().atZone(zone).toOffsetDateTime());
	}

}

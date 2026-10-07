package com.muhjain.school.staff;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.muhjain.school.vehicle.PaperStatus;

/**
 * One person. For a DRIVER, {@code licenceStatus} is calculated like a vehicle paper (VALID, ENDING_SOON, ENDED).
 * For other people the licence fields are null.
 * Example: {@code { "id": 21, "name": "Jagdish", "phone": "+919812340010", "staffType": "DRIVER",
 * "licenceNo": "HR2620110012345", "licenceValidTill": "2029-03-31", "licenceStatus": "VALID",
 * "licenceDaysLeft": 906, "active": true }}
 */
public record StaffResponse(Long id, String name, String phone, StaffType staffType, String licenceNo,
		LocalDate licenceValidTill, PaperStatus licenceStatus, Long licenceDaysLeft, boolean active) {

	static StaffResponse of(Staff staff, LocalDate today) {
		boolean driver = staff.getStaffType() == StaffType.DRIVER;
		LocalDate validTill = staff.getLicenceValidTill();
		return new StaffResponse(staff.getId(), staff.getName(), staff.getPhone(), staff.getStaffType(),
				staff.getLicenceNo(), validTill, driver ? PaperStatus.of(validTill, today) : null,
				(driver && validTill != null) ? ChronoUnit.DAYS.between(today, validTill) : null, staff.isActive());
	}

}

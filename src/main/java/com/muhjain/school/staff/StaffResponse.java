package com.muhjain.school.staff;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.muhjain.school.vehicle.PaperStatus;

/**
 * One employee. For a DRIVER, {@code licenceStatus} is calculated like a vehicle paper (VALID, ENDING_SOON, ENDED).
 * For other people the licence fields are null. {@code teaching} is filled only for a TEACHER.
 * <p>
 * There is no salary here on purpose: this list is open to VEHICLES_VIEW, and the transport in-charge must not
 * see what a teacher is paid (decision B24). The salary has its own URL and its own permission.
 * <p>
 * Example: {@code { "id": 21, "name": "Jagdish", "phone": "+919812340010", "staffType": "DRIVER",
 * "licenceNo": "HR2620110012345", "licenceValidTill": "2029-03-31", "licenceStatus": "VALID",
 * "licenceDaysLeft": 906, "active": true, "worksOn": { "vehicleId": 4, "vehicleName": "Van 4", "duty": "DRIVER",
 * "temporary": false }, "details": { "joinedOn": "2024-04-01" }, "teaching": null }}.
 * {@code worksOn} is where the person works today, or null if they are on no vehicle today.
 */
public record StaffResponse(Long id, String name, String phone, StaffType staffType, String licenceNo,
		LocalDate licenceValidTill, PaperStatus licenceStatus, Long licenceDaysLeft, boolean active,
		Placement worksOn, EmployeeDetails details, Teaching teaching) {

	static StaffResponse of(Staff staff, LocalDate today, Placement worksOn) {
		return of(staff, today, worksOn, null);
	}

	static StaffResponse of(Staff staff, LocalDate today, Placement worksOn, TeacherProfile profile) {
		boolean driver = staff.getStaffType() == StaffType.DRIVER;
		LocalDate validTill = staff.getLicenceValidTill();
		return new StaffResponse(staff.getId(), staff.getName(), staff.getPhone(), staff.getStaffType(),
				staff.getLicenceNo(), validTill, driver ? PaperStatus.of(validTill, today) : null,
				(driver && validTill != null) ? ChronoUnit.DAYS.between(today, validTill) : null, staff.isActive(),
				worksOn, EmployeeDetails.of(staff), (profile == null) ? null : Teaching.of(profile));
	}

}

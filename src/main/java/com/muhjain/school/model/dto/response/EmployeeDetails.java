package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.entity.Staff;
import com.muhjain.school.model.enums.IdProofType;
import com.muhjain.school.model.enums.StaffGender;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * The office file of one employee. Every field may be left out: the people added in Phase 2 have none of
 * this filled in.
 * {@code idProofType} and {@code idProofLast4} go together: send both or neither.
 * Example: {@code { "joinedOn": "2024-04-01", "dateOfBirth": "1988-07-12", "gender": "MALE",
 * "address": "Ward 7, Tohana", "emergencyPhone": "98123 40099", "idProofType": "AADHAAR",
 * "idProofLast4": "4321" }}
 */
public record EmployeeDetails(LocalDate joinedOn, LocalDate dateOfBirth, StaffGender gender,
		@Size(max = 300) String address, String emergencyPhone, IdProofType idProofType,
		@Pattern(regexp = "\\d{4}", message = "must be the last 4 digits") String idProofLast4) {

	public static final EmployeeDetails EMPTY = new EmployeeDetails(null, null, null, null, null, null, null);

	public static EmployeeDetails of(Staff staff) {
	return new EmployeeDetails(staff.getJoinedOn(), staff.getDateOfBirth(), staff.getGender(),
				staff.getAddress(), staff.getEmergencyPhone(), staff.getIdProofType(), staff.getIdProofLast4());
	}

}

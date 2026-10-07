package com.muhjain.school.student;

import java.time.LocalDate;
import java.util.List;

/**
 * The full profile of one student (screen 10). {@code busNow} is the bus today, null if the child has none.
 * {@code hasPhoto} says if a photo exists. The photo itself is only at {@code GET /students/{id}/photo}.
 */
public record StudentResponse(Long id, String admissionNo, String name, LocalDate dob, Gender gender,
		String className, String section, String village, String address, FatherOccupation fatherOccupation,
		StudentStatus status, LocalDate joinedOn, LocalDate leftOn, boolean hasPhoto, List<GuardianResponse> guardians,
		EnrolmentResponse busNow) {

}

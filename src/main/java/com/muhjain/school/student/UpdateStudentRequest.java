package com.muhjain.school.student;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The whole edit form of a student, like the other PUT requests. The admission number, the joining day and the
 * status are not here: the admission number never changes, and leaving has its own step.
 * Example: {@code { "name": "Aryan", "dob": "2018-05-14", "gender": "M", "className": "3", "section": "A",
 * "village": "Jakhal", "address": null, "fatherOccupation": "FARMER_SMALL" }}
 */
public record UpdateStudentRequest(@NotBlank @Size(max = 120) String name, @NotNull LocalDate dob,
		@NotNull Gender gender, @NotBlank String className, @Size(max = 4) String section,
		@NotBlank @Size(max = 80) String village, @Size(max = 200) String address,
		@NotNull FatherOccupation fatherOccupation) {

}

package com.muhjain.school.staff;

import jakarta.validation.constraints.Size;

/**
 * The extra file of a teacher. Sent and read only when {@code staffType} is TEACHER.
 * {@code classTeacherOf} is one class of this school, or left out when the teacher has no class.
 * Example: {@code { "qualification": "B.Ed, M.A. Hindi", "subjects": "Hindi, Social Science",
 * "classTeacherOf": "3" }}
 */
public record Teaching(@Size(max = 120) String qualification, @Size(max = 200) String subjects,
		@Size(max = 20) String classTeacherOf) {

	static final Teaching EMPTY = new Teaching(null, null, null);

	static Teaching of(TeacherProfile profile) {
		return new Teaching(profile.getQualification(), profile.getSubjects(), profile.getClassTeacherOf());
	}

}

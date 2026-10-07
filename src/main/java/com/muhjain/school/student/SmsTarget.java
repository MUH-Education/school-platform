package com.muhjain.school.student;

import java.util.List;

/**
 * What the messaging feature needs to write a parent SMS for one child: the name, gender and class, and the phones
 * that have SMS turned on. Example: Aryan Jain, M, class 3, phones of the father and the mother.
 */
public record SmsTarget(Long studentId, String name, Gender gender, String className, List<Phone> phones) {

	/** One parent phone with SMS on. */
	public record Phone(Long guardianId, String phone) {

	}

}

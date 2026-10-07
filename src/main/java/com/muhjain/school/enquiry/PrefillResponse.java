package com.muhjain.school.enquiry;

import java.time.LocalDate;

import com.muhjain.school.student.GuardianRelation;

/**
 * What the New admission screen can copy from an enquiry, and the text of the blue box "Started from the enquiry of
 * ...". The screen sends {@code enquiryId} again with {@code POST /admissions}.
 * Example: {@code Ramesh Jain, +919812340208, Jakhal, class 3, child Aryan} → "Started from the enquiry of Ramesh Jain
 * (5 Oct 2026)".
 *
 * @param guardian the parent who asked, to be added as the first phone
 * @param needsBus YES, NO or UNKNOWN: the screen may open the bus part when it is YES
 */
public record PrefillResponse(Long enquiryId, String childName, String className, String village,
		NeedsBus needsBus, Guardian guardian, String startedFrom, LocalDate enquiryDate) {

	/** The parent who made the enquiry. {@code relation} is OTHER when the clerk typed something else. */
	public record Guardian(String name, String phone, GuardianRelation relation) {

	}

}

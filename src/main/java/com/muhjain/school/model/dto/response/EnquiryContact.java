package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.EnquiryStatus;

/**
 * An enquiry as a message recipient. Example: enquiry 77, "Suresh Malik", +919812345678, "Riya", class 1, VISITED.
 * {@code childName} may be null.
 */
public record EnquiryContact(Long id, String parentName, String phone, String childName, String classSought,
		EnquiryStatus status) {
}

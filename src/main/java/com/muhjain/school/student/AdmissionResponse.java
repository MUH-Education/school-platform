package com.muhjain.school.student;

import java.math.BigDecimal;

/**
 * The answer of {@code POST /admissions}. {@code warning} is set only when the route is over its seats.
 * {@code receiptNo} is set when a first payment was recorded. {@code stillToPay} is set when a fee plan was made:
 * everything the family still owes this session.
 * Example: {@code { "studentId": 118, "admissionNo": "A-2026-118", "name": "Aryan", "warning": null,
 * "receiptNo": "R-2026-0412", "stillToPay": 29100.00 }}
 */
public record AdmissionResponse(Long studentId, String admissionNo, String name, TransportWarning warning,
		String receiptNo, BigDecimal stillToPay) {

}

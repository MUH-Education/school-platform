package com.muhjain.school.student;

/**
 * The answer of {@code POST /admissions}. {@code warning} is set only when the route is over its seats.
 * Example: {@code { "studentId": 118, "admissionNo": "A-2026-118", "name": "Aryan", "warning": null }}
 */
public record AdmissionResponse(Long studentId, String admissionNo, String name, TransportWarning warning) {

}

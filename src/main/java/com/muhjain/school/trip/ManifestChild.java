package com.muhjain.school.trip;

/**
 * A child in the manifest. There is no phone number here (decision C10).
 * Example: {@code Aryan, class 3 B, M, events...}.
 */
public record ManifestChild(Long studentId, String name, String admissionNo, String gender, String className,
		String section, EventsResponse events) {

}

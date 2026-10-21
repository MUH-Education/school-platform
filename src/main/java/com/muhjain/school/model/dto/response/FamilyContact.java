package com.muhjain.school.model.dto.response;

/**
 * The phone a family is reached on: the primary guardian of a child (the oldest phone when none is primary).
 * Example: guardian 210, "Rajesh Punia", +919811100001. {@code name} may be null.
 */
public record FamilyContact(Long guardianId, String name, String phone) {
}

package com.muhjain.school.student;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * A child leaves school, or comes back after a wrong entry.
 * <ul>
 * <li>Leaves: {@code { "status": "LEFT", "leftOn": "2026-10-20" }}. {@code leftOn} is optional and means today. It
 * cannot be in the future or before the joining day. The open bus row ends on that day.</li>
 * <li>Back: {@code { "status": "ACTIVE" }}. The bus is not restored, start it again from the student page.</li>
 * </ul>
 */
public record UpdateStatusRequest(@NotNull StudentStatus status, LocalDate leftOn) {

}

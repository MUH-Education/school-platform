package com.muhjain.school.student;

import java.time.OffsetDateTime;

/**
 * One line of the "Change history" box: what changed, who did it, and when.
 * Example: {@code "Section changed from B to A.", changedBy "Neelam", changedAt 2026-10-07T10:15:00+05:30}.
 * {@code changedBy} is null when no user did it (for example a start-up job).
 */
public record HistoryItem(String summary, String action, String changedBy, OffsetDateTime changedAt) {

}

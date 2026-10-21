package com.muhjain.school.model.dto.response;

import java.util.List;

/**
 * Who gets a notification right now. Made fresh every time (at draft time for the counts, at send time for the
 * rows), never stored. {@code stopped} are the families on the stop list, kept so the dispatcher can write them as
 * SKIPPED rows and the report can show them.
 */
public record ResolvedAudience(List<Recipient> recipients, List<Recipient> stopped, AudienceCounts counts) {
}

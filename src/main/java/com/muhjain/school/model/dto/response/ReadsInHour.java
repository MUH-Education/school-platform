package com.muhjain.school.model.dto.response;

import java.time.OffsetDateTime;

/** How many people read the message in one hour (school time). Example: 10:00-10:59 → 41. */
public record ReadsInHour(OffsetDateTime hour, long reads) {
}

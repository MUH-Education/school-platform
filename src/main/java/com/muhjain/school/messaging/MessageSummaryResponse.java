package com.muhjain.school.messaging;

import java.time.LocalDate;

/** Counts for one day. Example: 41 queued messages, 380 sent, 2 failed. */
public record MessageSummaryResponse(LocalDate date, long queued, long sent, long failed, long testOnly, long total) {

}

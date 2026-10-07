package com.muhjain.school.trip;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Called when a tap becomes DONE for the first time. Phase 5 puts the parent SMS behind it.
 * It runs inside the same transaction as the save, so a saved tap and its SMS row are saved together or not at all.
 * Example: Balwan taps Aryan as BOARDED_MORNING DONE at 07:42 → {@code onDone(118, BOARDED_MORNING, 7 Oct, 07:42)}.
 * A repeated tap, an ABSENT tap, or a correction of a DONE tap does not call it.
 */
public interface BoardingNotifier {

	void onDone(Long studentId, EventType eventType, LocalDate serviceDate, Instant occurredAt);

}

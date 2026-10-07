package com.muhjain.school.trip;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

/** Does nothing. Phase 5 replaces it with the real one. No SMS is sent in Phase 4. */
@Component
public class NoOpBoardingNotifier implements BoardingNotifier {

	@Override
	public void onDone(Long studentId, EventType eventType, LocalDate serviceDate, Instant occurredAt) {
	}

}

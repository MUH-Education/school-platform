package com.muhjain.school.student;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes the admission number: {@code A-<year>-<running number>}. Rule 2 of phase 3.
 * Example: the 118th admission of 2026 is "A-2026-118".
 * <p>
 * The counter row is locked until the transaction ends. So two clerks at the same time never get the same
 * number, and a number is not lost if an admission fails and rolls back.
 */
@Service
public class AdmissionNumberService {

	private final AdmissionCounterRepository counters;

	private final Clock clock;

	public AdmissionNumberService(AdmissionCounterRepository counters, Clock clock) {
		this.counters = counters;
		this.clock = clock;
	}

	/** The next free number of this year, in the school zone. Call it inside the admission transaction. */
	@Transactional
	public String next() {
		int year = LocalDate.now(clock).getYear();
		return "A-" + year + "-" + counters.next(year);
	}

}

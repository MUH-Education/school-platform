package com.muhjain.school.fee;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes the receipt number: {@code R-<session start year>-<4 digits>}. Rule 11 of phase 7.
 * Example: the 412th receipt of session 2026-27 is "R-2026-0412".
 * <p>
 * The counter row is locked until the transaction ends, like the admission counter. So two payments at the same
 * moment never get the same number, and a number is not lost if a payment fails and rolls back.
 */
@Service
public class ReceiptNumberService {

	private final ReceiptCounterRepository counters;

	public ReceiptNumberService(ReceiptCounterRepository counters) {
		this.counters = counters;
	}

	/** The next free number of this session. Call it inside the payment transaction. */
	@Transactional
	public String next(AcademicSession session) {
		return format(session.getStartsOn().getYear(), counters.next(session.getId()));
	}

	/** Example: (2026, 412) → "R-2026-0412". More than 9999 receipts grow to 5 digits, they are never cut. */
	static String format(int startYear, int number) {
		return "R-" + startYear + "-" + String.format("%04d", number);
	}

}

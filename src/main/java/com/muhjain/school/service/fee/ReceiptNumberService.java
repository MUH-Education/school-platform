package com.muhjain.school.service.fee;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.muhjain.school.model.enums.ReceiptKind;
import com.muhjain.school.model.repository.NumberCounterRepository;

/**
 * Makes the receipt number: {@code R-<year>-<running number>} for a payment, {@code RF-<year>-<running
 * number>} for a refund. Rule 11 of phase 7. Counter key: "RECEIPT-2026". Replaces the old session-scoped
 * {@code receipt_counter}: db-design counts receipts by calendar year, like admission numbers.
 * Example: the 1189th receipt of 2026 is "R-2026-1189".
 * <p>
 * The counter row is locked until the transaction ends, like the admission counter. So two payments at the
 * same moment never get the same number, and a number is not lost if a payment fails and rolls back.
 */
@Service
public class ReceiptNumberService {

	private final NumberCounterRepository counters;

	private final Clock clock;

	public ReceiptNumberService(NumberCounterRepository counters, Clock clock) {
		this.counters = counters;
		this.clock = clock;
	}

	/** The next free receipt number of this year. Call it inside the payment transaction. */
	@Transactional
	public String next() {
		return next(ReceiptKind.PAYMENT);
	}

	@Transactional
	public String next(ReceiptKind kind) {
		int year = LocalDate.now(clock).getYear();
		String prefix = (kind == ReceiptKind.REFUND) ? "RF" : "R";
		String counterKey = (kind == ReceiptKind.REFUND) ? "REFUND-" + year : "RECEIPT-" + year;
		return prefix + "-" + year + "-" + String.format("%04d", counters.next(counterKey));
	}

}

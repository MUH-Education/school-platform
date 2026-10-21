package com.muhjain.school.fee;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.service.fee.ReceiptNumberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rule 11 of phase 7: receipt numbers are R-<year>-<4 digits>, from a locked {@code number_counter} row.
 * Today is fixed to 7 Oct 2026, so the counter key is "RECEIPT-2026".
 */
class ReceiptNumberServiceTest extends AbstractIntegrationTest {

	@Autowired
	ReceiptNumberService receipts;

	@Autowired
	PlatformTransactionManager transactions;

	@BeforeEach
	void fixToday() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
	}

	private String nextInOwnTransaction() {
		return new TransactionTemplate(transactions).execute(status -> receipts.next());
	}

	@Test
	void numbersCountUpFromOne() {
		assertThat(nextInOwnTransaction()).isEqualTo("R-2026-0001");
		assertThat(nextInOwnTransaction()).isEqualTo("R-2026-0002");
	}

	@Test
	void aFailedTransactionGivesItsNumberBack() {
		assertThat(nextInOwnTransaction()).isEqualTo("R-2026-0001");
		assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> {
			receipts.next();
			throw new IllegalStateException("payment failed");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(nextInOwnTransaction()).isEqualTo("R-2026-0002");
	}

	@Test
	void twentyClerksAtTheSameMomentGetTwentyDifferentNumbers() throws Exception {
		int count = 20;
		ExecutorService pool = Executors.newFixedThreadPool(count);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<String>> results = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			results.add(pool.submit(() -> {
				go.await();
				// Each clerk keeps the transaction open a little, so the others really have to wait for the lock.
				return new TransactionTemplate(transactions).execute(s -> {
					String number = receipts.next();
					try {
						Thread.sleep(10);
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					return number;
				});
			}));
		}
		go.countDown();
		List<String> numbers = new ArrayList<>();
		for (Future<String> result : results) {
			numbers.add(result.get());
		}
		pool.shutdown();

		assertThat(numbers).doesNotHaveDuplicates().hasSize(count);
		assertThat(numbers).contains("R-2026-0001", "R-2026-0020");
	}

}

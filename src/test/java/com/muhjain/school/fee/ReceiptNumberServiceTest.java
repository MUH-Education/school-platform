package com.muhjain.school.fee;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rule 11 of phase 7: receipt numbers are R-<start year>-<4 digits>, from a locked counter. */
class ReceiptNumberServiceTest extends AbstractIntegrationTest {

	@Autowired
	ReceiptNumberService receipts;

	@Autowired
	AcademicSessionRepository sessions;

	@Autowired
	PlatformTransactionManager transactions;

	@Test
	void formatIsStartYearAndFourDigits() {
		assertThat(ReceiptNumberService.format(2026, 1)).isEqualTo("R-2026-0001");
		assertThat(ReceiptNumberService.format(2026, 412)).isEqualTo("R-2026-0412");
		assertThat(ReceiptNumberService.format(2026, 10000)).isEqualTo("R-2026-10000");
	}

	private String nextInOwnTransaction(AcademicSession session) {
		return new TransactionTemplate(transactions).execute(status -> receipts.next(session));
	}

	@Test
	void numbersCountUpFromOne() {
		AcademicSession session = sessions.findByCurrentTrue().orElseThrow();

		assertThat(nextInOwnTransaction(session)).isEqualTo("R-2026-0001");
		assertThat(nextInOwnTransaction(session)).isEqualTo("R-2026-0002");
	}

	@Test
	void aFailedTransactionGivesItsNumberBack() {
		AcademicSession session = sessions.findByCurrentTrue().orElseThrow();

		assertThat(nextInOwnTransaction(session)).isEqualTo("R-2026-0001");
		assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> {
			receipts.next(session);
			throw new IllegalStateException("payment failed");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(nextInOwnTransaction(session)).isEqualTo("R-2026-0002");
	}

	@Test
	void twentyClerksAtTheSameMomentGetTwentyDifferentNumbers() throws Exception {
		AcademicSession session = sessions.findByCurrentTrue().orElseThrow();
		int count = 20;
		ExecutorService pool = Executors.newFixedThreadPool(count);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<String>> results = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			results.add(pool.submit(() -> {
				go.await();
				// Each clerk keeps the transaction open a little, so the others really have to wait for the lock.
				return new TransactionTemplate(transactions).execute(s -> {
					String number = receipts.next(session);
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

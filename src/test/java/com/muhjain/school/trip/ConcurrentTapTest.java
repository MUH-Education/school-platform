package com.muhjain.school.trip;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 4.13. Two phones send the same tap at the same moment (bad signal, a retry). The unique row
 * (student, day, event) must stay one row, nothing may fail, and the SMS hook must run only once.
 */
class ConcurrentTapTest extends TripTestBase {

	@MockitoSpyBean
	private NoOpBoardingNotifier notifier;

	private void inParallel(Callable<?> first, Callable<?> second) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (Callable<?> task : List.of(first, second)) {
			futures.add(pool.submit(() -> {
				go.await();
				return task.call();
			}));
		}
		go.countDown();
		for (Future<?> future : futures) {
			future.get();
		}
		pool.shutdown();
	}

	private void sendOk(String... marks) throws Exception {
		sendMarks(balwanToken, marks).andExpect(status().isOk()).andExpect(jsonPath("$.results[0].ok").value(true));
	}

	@Test
	void sameTapFromTwoThreadsIsSavedOnce() throws Exception {
		for (int round = 0; round < 10; round++) {
			String day = (round % 2 == 0) ? TODAY : "2026-10-06";
			EventType[] types = EventType.values();
			String type = types[(round / 2) % types.length].name();
			String tap = mark(aryan, type, "DONE", day, day + "T07:42:10+05:30");
			inParallel(() -> {
				sendOk(tap);
				return null;
			}, () -> {
				sendOk(tap);
				return null;
			});
		}
		// 10 rounds = 2 days x 4 types, some keys twice. Never more than one row per (child, day, event).
		assertThat(jdbc.queryForObject("select count(*) from (select student_id, service_date, event_type from "
				+ "boarding_event group by 1, 2, 3 having count(*) > 1) d", Integer.class)).isZero();
		assertThat(rows()).isEqualTo(8);
	}

	@Test
	void onlyOneThreadTellsTheNotifier() throws Exception {
		String tap = morning(aryan, "DONE", "07:42:10");
		inParallel(() -> {
			sendOk(tap);
			return null;
		}, () -> {
			sendOk(tap);
			return null;
		});
		assertThat(rows()).isEqualTo(1);
		verify(notifier, times(1)).onDone(eq(aryan), eq(EventType.BOARDED_MORNING), any(), any());
	}

	@Test
	void newerTapWinsWhateverThreadIsFirst() throws Exception {
		// DONE at 07:42 and ABSENT at 07:43 sent at the same moment: ABSENT must be the final answer.
		for (int round = 0; round < 8; round++) {
			String day = (round % 2 == 0) ? TODAY : "2026-10-06";
			String type = EventType.values()[(round / 2) % EventType.values().length].name();
			String older = mark(siya, type, "DONE", day, day + "T07:42:00+05:30");
			String newer = mark(siya, type, "ABSENT", day, day + "T07:43:00+05:30");
			inParallel(() -> {
				sendOk(older);
				return null;
			}, () -> {
				sendOk(newer);
				return null;
			});
			assertThat(jdbc.queryForObject("select outcome from boarding_event where student_id = ? and "
					+ "service_date = ?::date and event_type = ?", String.class, siya, day, type)).isEqualTo("ABSENT");
		}
		assertThat(rows()).isEqualTo(8);
	}

}

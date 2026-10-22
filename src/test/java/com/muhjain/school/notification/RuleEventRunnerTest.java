package com.muhjain.school.notification;

import com.muhjain.school.service.notification.RuleEventRunner;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** The thread that does WELCOME / BUS_LATE after a commit. No Spring needed. */
class RuleEventRunnerTest {

	@Test
	void asyncRunsOnItsOwnThread() throws Exception {
		RuleEventRunner runner = new RuleEventRunner(true);
		CompletableFuture<String> thread = new CompletableFuture<>();
		runner.run(() -> thread.complete(Thread.currentThread().getName()));
		assertThat(thread.get(5, TimeUnit.SECONDS)).startsWith("rule-event-");
	}

	@Test
	void notAsyncRunsAtOnceOnTheCallersThread() {
		RuleEventRunner runner = new RuleEventRunner(false);
		String[] thread = new String[1];
		runner.run(() -> thread[0] = Thread.currentThread().getName());
		assertThat(thread[0]).isEqualTo(Thread.currentThread().getName());
	}

}

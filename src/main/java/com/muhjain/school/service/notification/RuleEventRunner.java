package com.muhjain.school.service.notification;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Runs the WELCOME and BUS_LATE work after an admission or a "running late" tap has committed (Phase 12), on one or
 * two threads of its own. On its own thread, the request first gives its database connection back; done on the
 * request thread, every such request would hold two connections at once, and 10 at the same moment would empty the
 * pool. It is not an {@code Executor} bean on purpose, so Spring Boot still makes its own task executor.
 * <p>
 * Tests set {@code app.notifications.events-async=false}: the work is then done at once on the same thread, so a test
 * can check the result straight after the call.
 */
@Component
public class RuleEventRunner {

	private final ThreadPoolTaskExecutor pool;

	public RuleEventRunner(@Value("${app.notifications.events-async:true}") boolean async) {
		if (!async) {
			this.pool = null;
			return;
		}
		pool = new ThreadPoolTaskExecutor();
		pool.setThreadNamePrefix("rule-event-");
		pool.setCorePoolSize(1);
		pool.setMaxPoolSize(2);
		pool.setQueueCapacity(500);
		// A full queue (very unlikely): the request thread does the work itself, nothing is lost.
		pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		pool.setWaitForTasksToCompleteOnShutdown(true);
		pool.setAwaitTerminationSeconds(20);
		pool.initialize();
	}

	public void run(Runnable work) {
		if (pool == null) {
			work.run();
		}
		else {
			pool.execute(work);
		}
	}

	@PreDestroy
	void stop() {
		if (pool != null) {
			pool.shutdown();
		}
	}

}

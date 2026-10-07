package com.muhjain.school.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Calls the worker every 5 seconds. Off in tests ({@code app.messaging.worker-enabled=false}). */
@Component
@ConditionalOnProperty(name = "app.messaging.worker-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {

	private final OutboxWorker worker;

	public OutboxScheduler(OutboxWorker worker) {
		this.worker = worker;
	}

	@Scheduled(fixedDelay = 5000)
	public void send() {
		worker.runOnce();
	}

}

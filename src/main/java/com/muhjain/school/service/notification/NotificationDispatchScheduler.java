package com.muhjain.school.service.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Calls the dispatcher every 60 seconds. Off in tests ({@code app.notifications.dispatcher-enabled=false}). */
@Component
@ConditionalOnProperty(name = "app.notifications.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDispatchScheduler {

	private final NotificationDispatcher dispatcher;

	public NotificationDispatchScheduler(NotificationDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
	public void dispatch() {
		dispatcher.runOnce();
	}

}

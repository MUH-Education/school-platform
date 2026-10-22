package com.muhjain.school.service.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Calls {@link RuleScheduler} every minute. Off in tests ({@code app.notifications.rules-enabled=false}). */
@Component
@ConditionalOnProperty(name = "app.notifications.rules-enabled", havingValue = "true", matchIfMissing = true)
public class RuleSchedulerJob {

	private final RuleScheduler scheduler;

	private final Clock clock;

	public RuleSchedulerJob(RuleScheduler scheduler, Clock clock) {
		this.scheduler = scheduler;
		this.clock = clock;
	}

	// At second 5 of every minute, so a 10:00 rule fires at 10:00:05.
	@Scheduled(cron = "5 * * * * *")
	public void tick() {
		scheduler.runOnce(Instant.now(clock));
	}

}

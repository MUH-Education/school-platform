package com.muhjain.school.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one {@link Clock} of the app, in the school zone (app.zone = Asia/Kolkata).
 * Code that needs "now" injects this clock, so tests can use a fixed time.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	public Clock clock(@Value("${app.zone}") String zone) {
		return Clock.system(ZoneId.of(zone));
	}

}

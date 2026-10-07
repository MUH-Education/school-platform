package com.muhjain.school.common;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Fills {@code created_at} and {@code updated_at} from the app {@link Clock}, not from the computer clock.
 * So a test with a fixed clock also gets fixed times in the database.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

	@Bean
	DateTimeProvider auditingDateTimeProvider(Clock clock) {
		// PostgreSQL keeps microseconds. Cut here, so the saved time and the time in Java are the same.
		return () -> Optional.of(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
	}

}

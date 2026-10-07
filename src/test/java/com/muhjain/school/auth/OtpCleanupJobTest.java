package com.muhjain.school.auth;

import java.time.Duration;
import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class OtpCleanupJobTest extends AbstractIntegrationTest {

	@Autowired
	private OtpCleanupJob job;

	@Autowired
	private OtpCodeRepository codes;

	@Test
	void codesOlderThanSevenDaysAreDeleted() {
		Instant start = Instant.parse("2026-10-01T03:30:00Z");
		clock.setInstant(start);
		codes.saveAndFlush(code());
		clock.setInstant(start.plus(Duration.ofDays(2)));
		OtpCode recent = codes.saveAndFlush(code());

		clock.setInstant(start.plus(Duration.ofDays(8)));
		job.deleteOldCodes();

		assertThat(codes.findAll()).extracting(OtpCode::getId).containsExactly(recent.getId());
	}

	private OtpCode code() {
		return new OtpCode("+919812340002", "a".repeat(64), "LOG", Instant.now(clock).plus(Duration.ofMinutes(5)),
				"10.0.0.1");
	}

}

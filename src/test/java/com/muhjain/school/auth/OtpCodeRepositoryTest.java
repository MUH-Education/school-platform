package com.muhjain.school.auth;

import java.time.Duration;
import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class OtpCodeRepositoryTest extends AbstractIntegrationTest {

	private static final String PHONE = "+919812340002";

	@Autowired
	private OtpCodeRepository codes;

	@Test
	void newestUnusedCodeIsFound() {
		Instant start = Instant.parse("2026-10-07T03:30:00Z");
		clock.setInstant(start);
		OtpCode first = codes.saveAndFlush(code("10.0.0.1"));
		clock.advance(Duration.ofMinutes(2));
		OtpCode second = codes.saveAndFlush(code("10.0.0.1"));

		assertThat(codes.findFirstByPhoneAndConsumedAtIsNullOrderByCreatedAtDescIdDesc(PHONE)).get()
			.extracting(OtpCode::getId)
			.isEqualTo(second.getId());

		second.consume(Instant.now(clock));
		codes.saveAndFlush(second);

		assertThat(codes.findFirstByPhoneAndConsumedAtIsNullOrderByCreatedAtDescIdDesc(PHONE)).get()
			.extracting(OtpCode::getId)
			.isEqualTo(first.getId());
		assertThat(codes.findFirstByPhoneOrderByCreatedAtDescIdDesc(PHONE)).get()
			.extracting(OtpCode::getId)
			.isEqualTo(second.getId());
	}

	@Test
	void countsInTheLastHourByPhoneAndByIp() {
		Instant start = Instant.parse("2026-10-07T03:30:00Z");
		clock.setInstant(start);
		codes.saveAndFlush(code("10.0.0.1"));
		clock.advance(Duration.ofMinutes(61));
		codes.saveAndFlush(code("10.0.0.2"));

		Instant oneHourAgo = Instant.now(clock).minus(Duration.ofHours(1));
		assertThat(codes.countByPhoneAndCreatedAtAfter(PHONE, oneHourAgo)).isEqualTo(1);
		assertThat(codes.countByRequestIpAndCreatedAtAfter("10.0.0.1", oneHourAgo)).isZero();
		assertThat(codes.countByRequestIpAndCreatedAtAfter("10.0.0.2", oneHourAgo)).isEqualTo(1);
	}

	private OtpCode code(String ip) {
		return new OtpCode(PHONE, "a".repeat(64), "LOG", Instant.now(clock).plus(Duration.ofMinutes(5)), ip);
	}

}

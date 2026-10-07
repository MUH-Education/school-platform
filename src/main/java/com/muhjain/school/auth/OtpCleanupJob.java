package com.muhjain.school.auth;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every night at 02:30 school time, deletes {@code otp_code} rows older than 7 days.
 * Old codes are useless, and the limits only look at the last hour.
 */
@Component
public class OtpCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(OtpCleanupJob.class);

	static final Duration KEEP = Duration.ofDays(7);

	private final OtpService otpService;

	public OtpCleanupJob(OtpService otpService) {
		this.otpService = otpService;
	}

	@Scheduled(cron = "0 30 2 * * *", zone = "${app.zone}")
	public void deleteOldCodes() {
		int deleted = otpService.deleteOlderThan(KEEP);
		log.info("Deleted {} OTP codes older than {} days", deleted, KEEP.toDays());
	}

}

package com.muhjain.school.service.auth.impl;

import com.muhjain.school.service.auth.OtpSender;
import com.muhjain.school.util.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Sends nothing. In the {@code local} profile it prints the code in the console:
 * {@code OTP for +91XXXXXX0002 is 482913}. In every other profile the code is hidden.
 * This is the only place in the app that may log a code (security rule 5).
 */
@Component
public class LogOtpSender implements OtpSender {

	private static final Logger log = LoggerFactory.getLogger(LogOtpSender.class);

	private final boolean local;

	public LogOtpSender(Environment environment) {
		this.local = environment.matchesProfiles("local");
	}

	@Override
	public String channel() {
		return "LOG";
	}

	@Override
	public void send(String phone, String code) {
		if (local) {
			log.info("OTP for {} is {}", PhoneNumbers.mask(phone), code);
		}
		else {
			log.info("OTP for {} made (code hidden outside local)", PhoneNumbers.mask(phone));
		}
	}

}

package com.muhjain.school;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock that tests can move. Example: {@code clock.advance(Duration.ofMinutes(6))} makes a 5-minute OTP too old.
 */
public class MutableClock extends Clock {

	private final ZoneId zone;

	private volatile Instant now;

	public MutableClock(Instant now, ZoneId zone) {
		this.now = now;
		this.zone = zone;
	}

	public void setInstant(Instant now) {
		this.now = now;
	}

	public void advance(Duration duration) {
		this.now = this.now.plus(duration);
	}

	@Override
	public Instant instant() {
		return now;
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(now, zone);
	}

}

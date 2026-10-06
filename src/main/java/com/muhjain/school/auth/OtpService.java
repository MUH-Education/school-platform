package com.muhjain.school.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.PhoneNumbers;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login codes: ask for one ({@link #request}) and check one ({@code verify}, task 1.10).
 * See docs/04-login-otp-jwt.md.
 */
@Service
public class OtpService {

	private static final Logger log = LoggerFactory.getLogger(OtpService.class);

	static final String TOO_MANY = "OTP_TOO_MANY_REQUESTS";

	private static final Duration ONE_HOUR = Duration.ofHours(1);

	private final OtpCodeRepository codes;

	private final OtpHasher hasher;

	private final OtpDeliveryService delivery;

	private final OtpProperties properties;

	private final UserService userService;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	public OtpService(OtpCodeRepository codes, OtpHasher hasher, OtpDeliveryService delivery,
			OtpProperties properties, UserService userService, Clock clock) {
		this.codes = codes;
		this.hasher = hasher;
		this.delivery = delivery;
		this.properties = properties;
		this.userService = userService;
		this.clock = clock;
	}

	/**
	 * Step A of the login. Example: "98123 40002" → a code is sent to Neelam, if she is an active user.
	 * The answer is the same for a known and an unknown phone, so a stranger cannot test numbers.
	 *
	 * @throws ApiException 400 VALIDATION for a bad phone, 429 OTP_TOO_MANY_REQUESTS over a limit
	 */
	@Transactional
	public OtpRequestResponse request(String rawPhone, String ip) {
		String phone = PhoneNumbers.normalize(rawPhone);
		Instant now = Instant.now(clock);
		checkLimits(phone, ip, now);

		Optional<AppUser> user = userService.findActiveByPhone(phone);
		if (user.isPresent()) {
			String code = newCode();
			try {
				String channel = delivery.send(phone, code);
				codes.save(new OtpCode(phone, hasher.hash(phone, code), channel, now.plus(properties.ttl()), ip));
			}
			catch (OtpSendException ex) {
				// Same answer as always, so the failure does not tell a stranger the number is known.
				log.error("Could not send an OTP to {}: {}", PhoneNumbers.mask(phone), ex.getMessage());
			}
		}
		return new OtpRequestResponse("If this number is registered, a code has been sent.",
				properties.ttl().toSeconds(), properties.resendAfter().toSeconds());
	}

	// Limits are counted from otp_code rows: 60 seconds between codes, 5 per phone and 20 per IP in one hour.
	private void checkLimits(String phone, String ip, Instant now) {
		Optional<OtpCode> last = codes.findFirstByPhoneOrderByCreatedAtDescIdDesc(phone);
		if (last.isPresent()) {
			Instant nextAllowed = last.get().getCreatedAt().plus(properties.resendAfter());
			if (now.isBefore(nextAllowed)) {
				throw tooMany(Duration.between(now, nextAllowed));
			}
		}
		Instant hourAgo = now.minus(ONE_HOUR);
		if (codes.countByPhoneAndCreatedAtAfter(phone, hourAgo) >= properties.maxPerHour()) {
			Instant oldest = codes.findFirstByPhoneAndCreatedAtAfterOrderByCreatedAtAsc(phone, hourAgo)
				.orElseThrow()
				.getCreatedAt();
			throw tooMany(Duration.between(now, oldest.plus(ONE_HOUR)));
		}
		if (ip != null && codes.countByRequestIpAndCreatedAtAfter(ip, hourAgo) >= properties.maxPerIpPerHour()) {
			Instant oldest = codes.findFirstByRequestIpAndCreatedAtAfterOrderByCreatedAtAsc(ip, hourAgo)
				.orElseThrow()
				.getCreatedAt();
			throw tooMany(Duration.between(now, oldest.plus(ONE_HOUR)));
		}
	}

	private static ApiException tooMany(Duration wait) {
		long seconds = Math.max(1, (wait.toMillis() + 999) / 1000);
		return ApiException.tooManyRequests(TOO_MANY,
				"Too many codes asked. Please wait " + seconds + " seconds and try again.", seconds);
	}

	// Example with length 6: 7 → "000007". SecureRandom, so it cannot be guessed.
	private String newCode() {
		int bound = (int) Math.pow(10, properties.length());
		return String.format("%0" + properties.length() + "d", random.nextInt(bound));
	}

}

package com.muhjain.school.service.auth;

import com.muhjain.school.config.OtpProperties;
import com.muhjain.school.exception.ApiException;
import com.muhjain.school.exception.OtpSendException;
import com.muhjain.school.model.dto.response.AuthUserResponse;
import com.muhjain.school.model.dto.response.IssuedToken;
import com.muhjain.school.model.dto.response.LoginResponse;
import com.muhjain.school.model.dto.response.OtpRequestResponse;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.entity.OtpWhitelist;
import com.muhjain.school.model.enums.AuditAction;
import com.muhjain.school.model.repository.OtpWhitelistRepository;
import com.muhjain.school.service.audit.AuditService;
import com.muhjain.school.service.user.UserService;
import com.muhjain.school.util.OtpHasher;
import com.muhjain.school.util.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Login codes: ask for one ({@link #request}) and check one ({@link #verify}). The code, its
 * attempts and the rate-limit windows all live in Redis ({@link RedisOtpStore}), not Postgres —
 * see docs/08-decisions.md. See docs/04-login-otp-jwt.md for the flow.
 */
@Service
public class OtpService {

	private static final Logger log = LoggerFactory.getLogger(OtpService.class);

	static final String TOO_MANY = "OTP_TOO_MANY_REQUESTS";

	static final String INVALID = "OTP_INVALID";

	static final String LOCKED = "OTP_LOCKED";

	private static final Duration ONE_HOUR = Duration.ofHours(1);

	static final String WHITELIST_CHANNEL = "WHITELIST";

	private final RedisOtpStore otpStore;

	private final OtpHasher hasher;

	private final OtpDeliveryService delivery;

	private final OtpProperties properties;

	private final UserService userService;

	private final SessionService sessionService;

	private final AuditService auditService;

	private final OtpWhitelistRepository whitelist;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	public OtpService(RedisOtpStore otpStore, OtpHasher hasher, OtpDeliveryService delivery,
			OtpProperties properties, UserService userService, SessionService sessionService,
			AuditService auditService, OtpWhitelistRepository whitelist, Clock clock) {
		this.otpStore = otpStore;
		this.hasher = hasher;
		this.delivery = delivery;
		this.properties = properties;
		this.userService = userService;
		this.sessionService = sessionService;
		this.auditService = auditService;
		this.whitelist = whitelist;
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
		Instant now = now();
		checkLimits(phone, ip, now);

		Optional<AppUser> user = userService.findActiveByPhone(phone);
		if (user.isPresent()) {
			if (whitelist.existsById(phone)) {
				// Example: +919999900001 is whitelisted -> code is always "123456", no WhatsApp/SMS goes out.
				String code = fixedCode();
				otpStore.save(phone, hasher.hash(phone, code), WHITELIST_CHANNEL, now.plus(properties.ttl()));
				recordWindows(phone, ip, now);
			}
			else {
				String code = newCode();
				try {
					String channel = delivery.send(phone, code);
					otpStore.save(phone, hasher.hash(phone, code), channel, now.plus(properties.ttl()));
					recordWindows(phone, ip, now);
				}
				catch (OtpSendException ex) {
					// Same answer as always, so the failure does not tell a stranger the number is known.
					log.error("Could not send an OTP to {}: {}", PhoneNumbers.mask(phone), ex.getMessage());
				}
			}
		}
		return new OtpRequestResponse("If this number is registered, a code has been sent.",
				properties.ttl().toSeconds(), properties.resendAfter().toSeconds());
	}

	/**
	 * Adds a phone to the fixed-OTP whitelist. Example: "9999900001" -> every future code for this number is
	 * "123456" (or "1234" if {@code app.otp.length} is 4), and no real message is sent. Does nothing if the
	 * phone is whitelisted already.
	 */
	@Transactional
	public void addToWhitelist(String rawPhone, String note) {
		String phone = PhoneNumbers.normalize(rawPhone);
		if (!whitelist.existsById(phone)) {
			whitelist.save(new OtpWhitelist(phone, note, now()));
		}
	}

	/**
	 * Step C of the login. Example: "98123 40002" + "482913" → a token for Neelam.
	 * Wrong code, old code, no code, unknown or turned-off user: all give the same 401 OTP_INVALID.
	 *
	 * @throws ApiException 401 OTP_INVALID, 429 OTP_LOCKED after too many wrong tries
	 */
	@Transactional
	public LoginResponse verify(String rawPhone, String code, String ip) {
		String phone = PhoneNumbers.normalize(rawPhone);
		Instant now = now();
		RedisOtpStore.ActiveCode otp = otpStore.find(phone).filter(row -> row.expiresAt().isAfter(now))
			.orElseThrow(OtpService::invalid);
		AppUser user = userService.findActiveByPhone(phone).orElseThrow(OtpService::invalid);
		if (otp.attempts() >= properties.maxAttempts()) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, LOCKED,
					"Too many wrong tries. Please ask for a new code.");
		}
		if (code == null || !hasher.matches(phone, code, otp.codeHash())) {
			otpStore.addWrongAttempt(phone);
			throw invalid();
		}

		otpStore.consume(phone);
		user = userService.recordLogin(user.getId(), now);
		auditService.record("USER", user.getId(), AuditAction.LOGIN, "Logged in by OTP (" + otp.channel() + ")", null,
				user.getId());
		IssuedToken token = sessionService.create(user, ip);
		return new LoginResponse(token.token(), token.expiresAt().atZone(clock.getZone()).toOffsetDateTime(),
				AuthUserResponse.of(user));
	}

	private static ApiException invalid() {
		return new ApiException(HttpStatus.UNAUTHORIZED, INVALID, "The code is wrong or too old.");
	}

	// PostgreSQL keeps microseconds, so "now" is cut to microseconds too (kept for consistency with
	// the rest of the app, even though OTP state no longer lives in PostgreSQL).
	private Instant now() {
		return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

	// Limits are read from the Redis windows: 60 seconds between codes, 5 per phone and 20 per IP in one hour.
	// A phone that turns out to be unknown never calls recordWindows, so it never counts against these limits.
	private void checkLimits(String phone, String ip, Instant now) {
		List<Instant> phoneWindow = otpStore.activeInWindow("phone:" + phone, now, ONE_HOUR);
		if (!phoneWindow.isEmpty()) {
			Instant last = phoneWindow.get(phoneWindow.size() - 1);
			Instant nextAllowed = last.plus(properties.resendAfter());
			if (now.isBefore(nextAllowed)) {
				throw tooMany(Duration.between(now, nextAllowed));
			}
		}
		if (phoneWindow.size() >= properties.maxPerHour()) {
			Instant oldest = phoneWindow.get(0);
			throw tooMany(Duration.between(now, oldest.plus(ONE_HOUR)));
		}
		if (ip != null) {
			List<Instant> ipWindow = otpStore.activeInWindow("ip:" + ip, now, ONE_HOUR);
			if (ipWindow.size() >= properties.maxPerIpPerHour()) {
				throw tooMany(Duration.between(now, ipWindow.get(0).plus(ONE_HOUR)));
			}
		}
	}

	private void recordWindows(String phone, String ip, Instant now) {
		otpStore.record("phone:" + phone, now, ONE_HOUR);
		if (ip != null) {
			otpStore.record("ip:" + ip, now, ONE_HOUR);
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

	// The one fixed code for a whitelisted phone: "1234" for a 4-digit setup, "123456" for anything else.
	private String fixedCode() {
		return (properties.length() == 4) ? "1234" : "123456";
	}

}

package com.muhjain.school.service.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Where a login code and its rate-limit counters live: Redis, not Postgres (docs/08-decisions.md).
 * Every check compares a stored time against the app {@link java.time.Clock}; Redis's own key
 * expiry (TTL) is only a safety net to clean up old keys, never what decides pass or fail — the
 * same reason tests can move a fixed {@code Clock} forward instead of really waiting.
 */
@Component
public class RedisOtpStore {

	private static final String CODE_PREFIX = "otp:code:";

	private static final String WINDOW_PREFIX = "otp:window:";

	// How much longer than the real TTL a key is kept, just so it cleans itself up eventually.
	private static final Duration CLEANUP_BUFFER = Duration.ofMinutes(5);

	private final StringRedisTemplate redis;

	public RedisOtpStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** One active code per phone: saving a new one replaces whatever was there before. */
	public void save(String phone, String codeHash, String channel, Instant expiresAt) {
		String key = CODE_PREFIX + phone;
		Map<String, String> fields = Map.of("codeHash", codeHash, "channel", channel, "expiresAt",
				String.valueOf(expiresAt.toEpochMilli()), "attempts", "0");
		redis.opsForHash().putAll(key, fields);
		redis.expire(key, Duration.between(Instant.now(), expiresAt).plus(CLEANUP_BUFFER));
	}

	/** The active code of a phone, if any is still on file. The caller checks {@code expiresAt} itself. */
	public Optional<ActiveCode> find(String phone) {
		Map<Object, Object> fields = redis.opsForHash().entries(CODE_PREFIX + phone);
		if (fields.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new ActiveCode((String) fields.get("codeHash"), (String) fields.get("channel"),
				Instant.ofEpochMilli(Long.parseLong((String) fields.get("expiresAt"))),
				Integer.parseInt((String) fields.get("attempts"))));
	}

	public void addWrongAttempt(String phone) {
		redis.opsForHash().increment(CODE_PREFIX + phone, "attempts", 1);
	}

	/** A right code cannot be used twice. */
	public void consume(String phone) {
		redis.delete(CODE_PREFIX + phone);
	}

	/**
	 * Every time still inside the window for this key, oldest first. Drops anything older as it
	 * reads, so this is also how old entries get removed (no separate cleanup job is needed).
	 */
	public List<Instant> activeInWindow(String key, Instant now, Duration window) {
		String redisKey = WINDOW_PREFIX + key;
		redis.opsForZSet().removeRangeByScore(redisKey, 0, now.minus(window).toEpochMilli());
		Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().rangeWithScores(redisKey, 0, -1);
		if (tuples == null) {
			return List.of();
		}
		return tuples.stream()
			.map(ZSetOperations.TypedTuple::getScore)
			.filter(Objects::nonNull)
			.map(score -> Instant.ofEpochMilli(score.longValue()))
			.sorted()
			.toList();
	}

	/** Adds "now" into the window. Call only when a code is actually being sent (never for an unknown phone). */
	public void record(String key, Instant now, Duration window) {
		String redisKey = WINDOW_PREFIX + key;
		redis.opsForZSet().add(redisKey, UUID.randomUUID().toString(), now.toEpochMilli());
		redis.expire(redisKey, window.plus(CLEANUP_BUFFER));
	}

	/** The active code of a phone: its hash, which channel sent it, when it expires, and how many wrong tries. */
	public record ActiveCode(String codeHash, String channel, Instant expiresAt, int attempts) {
	}

}

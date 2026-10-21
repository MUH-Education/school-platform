package com.muhjain.school.util;

import com.muhjain.school.security.SessionProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * HMAC-SHA256 of a raw session id with {@code app.session.hash-secret}. The id itself has 32 random
 * bytes of its own, so unlike {@link OtpHasher} nothing else needs to go into the hash.
 * <p>
 * Only the hash is ever saved in Postgres ({@code user_session.session_hash}): a stolen database
 * row must not be a usable session, the same reason an OTP code is never stored as plain text.
 */
@Component
public class SessionIdHasher {

	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec key;

	public SessionIdHasher(SessionProperties properties) {
		this.key = new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	public String hash(String sessionId) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			byte[] digest = mac.doFinal(sessionId.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException("HmacSHA256 is not available", ex);
		}
	}

	/** Compares in constant time, so the time taken does not tell how many characters were right. */
	public boolean matches(String sessionId, String expectedHash) {
		byte[] actual = hash(sessionId).getBytes(StandardCharsets.US_ASCII);
		return MessageDigest.isEqual(actual, expectedHash.getBytes(StandardCharsets.US_ASCII));
	}

}

package com.muhjain.school.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 of a code with {@code app.otp.hash-secret}. The phone is part of the input,
 * so the same code for another phone gives another hash.
 * Example: hash("+919812340002", "482913") → 64 hex characters.
 */
@Component
public class OtpHasher {

	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec key;

	public OtpHasher(OtpProperties properties) {
		this.key = new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	public String hash(String phone, String code) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			byte[] digest = mac.doFinal((phone + ":" + code).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException("HmacSHA256 is not available", ex);
		}
	}

	/** Compares in constant time, so the time taken does not tell how many characters were right. */
	public boolean matches(String phone, String code, String expectedHash) {
		byte[] actual = hash(phone, code).getBytes(StandardCharsets.US_ASCII);
		return MessageDigest.isEqual(actual, expectedHash.getBytes(StandardCharsets.US_ASCII));
	}

}

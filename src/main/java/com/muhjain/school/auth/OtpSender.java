package com.muhjain.school.auth;

/**
 * One way to send a login code. Phase 1 has only {@link LogOtpSender}. WhatsApp and SMS come in Phase 5.
 */
public interface OtpSender {

	/** "WHATSAPP", "SMS" or "LOG". Saved in {@code otp_code.channel}. */
	String channel();

	void send(String phone, String code) throws OtpSendException;

}

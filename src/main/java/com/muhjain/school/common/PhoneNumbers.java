package com.muhjain.school.common;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

/**
 * Turns what a person types into one stored form: {@code +91XXXXXXXXXX}.
 * Example: "98123 45678", "09812345678" and "+91-98123-45678" all become "+919812345678".
 */
public final class PhoneNumbers {

	private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().]");

	// An Indian mobile number: 10 digits, the first one 6 to 9.
	private static final Pattern MOBILE = Pattern.compile("[6-9]\\d{9}");

	private PhoneNumbers() {
	}

	/**
	 * @return the phone as {@code +91XXXXXXXXXX}
	 * @throws ApiException 400 {@code VALIDATION} if it is not an Indian mobile number
	 */
	public static String normalize(String input) {
		if (input == null) {
			throw invalid();
		}
		String phone = SEPARATORS.matcher(input.strip()).replaceAll("");
		String digits;
		if (phone.startsWith("+91")) {
			digits = phone.substring(3);
		}
		else if (phone.length() == 12 && phone.startsWith("91")) {
			digits = phone.substring(2);
		}
		else if (phone.length() == 11 && phone.startsWith("0")) {
			digits = phone.substring(1);
		}
		else {
			digits = phone;
		}
		if (!MOBILE.matcher(digits).matches()) {
			throw invalid();
		}
		return "+91" + digits;
	}

	/**
	 * For logs: hides all but the last 4 digits. Example: "+919812344321" → "+91XXXXXX4321".
	 * Anything that is not a stored phone becomes "+91XXXXXXXXXX", so a typo is never logged in full.
	 */
	public static String mask(String phone) {
		if (phone == null || !phone.startsWith("+91") || phone.length() != 13) {
			return "+91XXXXXXXXXX";
		}
		return "+91XXXXXX" + phone.substring(9);
	}

	private static ApiException invalid() {
		return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
				"Enter a 10 digit Indian mobile number, like 98123 45678.");
	}

}

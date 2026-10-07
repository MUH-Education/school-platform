package com.muhjain.school.messaging;

import java.time.LocalTime;

/**
 * Fills a template with the child's first name and the tap time. Pure Java.
 * Example: {@code "{name} स्कूल पहुँच गया — {time}। MUH Jain School"}, "Aryan Jain", 07:42 →
 * {@code "Aryan स्कूल पहुँच गया — 7:42। MUH Jain School"}.
 * <p>
 * A Hindi SMS holds 70 characters in one part. A longer one costs double. If the name makes the text too long,
 * the name is shortened (the rest of the text is the DLT-approved wording and is never cut).
 */
public final class SmsTextBuilder {

	/** One Hindi (Unicode) SMS part. */
	public static final int MAX_LENGTH = 70;

	private SmsTextBuilder() {
	}

	/**
	 * @param template text with {@code {name}} and {@code {time}}
	 * @param childName full name; only the first word is used
	 * @param time tap time in the school zone
	 * @throws IllegalArgumentException if the template is too long even with a one-letter name
	 */
	public static String build(String template, String childName, LocalTime time) {
		String timeText = time.getHour() + ":" + String.format("%02d", time.getMinute());
		String name = firstWord(childName);
		String text = fill(template, name, timeText);
		while (text.length() > MAX_LENGTH && name.codePointCount(0, name.length()) > 1) {
			name = name.substring(0, name.offsetByCodePoints(name.length(), -1));
			text = fill(template, name, timeText);
		}
		if (text.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("SMS text is longer than " + MAX_LENGTH + " characters: " + text);
		}
		return text;
	}

	static String firstWord(String fullName) {
		String trimmed = (fullName == null) ? "" : fullName.trim();
		int space = trimmed.indexOf(' ');
		return (space < 0) ? trimmed : trimmed.substring(0, space);
	}

	private static String fill(String template, String name, String time) {
		return template.replace("{name}", name).replace("{time}", time);
	}

}

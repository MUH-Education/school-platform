package com.muhjain.school.common;

import java.util.Locale;

/**
 * Names that must be unique "without caring about capital letters or spaces".
 * Example: {@code key("hr 23 a 1104")} and {@code key("HR23A1104")} are both "HR23A1104", so they are the same.
 * The database has a unique index on the same formula: {@code upper(replace(name, ' ', ''))}.
 */
public final class NameKeys {

	private NameKeys() {
	}

	/** What is saved. White space is cut at both ends, and every run of it becomes one space. */
	public static String tidy(String text) {
		return (text == null) ? null : text.strip().replaceAll("\\s+", " ");
	}

	/** What is compared. Example: "Van 4" → "VAN4". */
	public static String key(String text) {
		return tidy(text).replace(" ", "").toUpperCase(Locale.ROOT);
	}

}

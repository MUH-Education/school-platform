package com.muhjain.school.student;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The classes of the school: Nursery, LKG, UKG, 1 to 12. {@code class_name} is saved in exactly this spelling.
 * Example: "lkg" and " LKG " both become "LKG". "7th" is not a class.
 */
public final class ClassNames {

	public static final List<String> ALL = List.of("Nursery", "LKG", "UKG", "1", "2", "3", "4", "5", "6", "7", "8", "9",
			"10", "11", "12");

	private ClassNames() {
	}

	/** @return the class in the stored spelling, or empty if it is not a class of this school */
	public static Optional<String> parse(String text) {
		if (text == null) {
			return Optional.empty();
		}
		String wanted = text.strip().toLowerCase(Locale.ROOT);
		return ALL.stream().filter(c -> c.toLowerCase(Locale.ROOT).equals(wanted)).findFirst();
	}

}

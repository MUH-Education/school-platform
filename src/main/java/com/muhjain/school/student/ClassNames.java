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

	/**
	 * One class or a group of classes written "from-to", in school order. Used by Analytics.
	 * Examples: "3" → [3]. "1-5" → [1, 2, 3, 4, 5]. "LKG-2" → [LKG, UKG, 1, 2]. "5-1" and "7th" → empty.
	 *
	 * @return the classes in the stored spelling, or empty if the text is not a class or a group
	 */
	public static Optional<List<String>> expand(String text) {
		if (text == null) {
			return Optional.empty();
		}
		Optional<String> single = parse(text);
		if (single.isPresent()) {
			return Optional.of(List.of(single.get()));
		}
		String[] ends = text.split("-", -1);
		if (ends.length != 2) {
			return Optional.empty();
		}
		Optional<String> from = parse(ends[0]);
		Optional<String> to = parse(ends[1]);
		if (from.isEmpty() || to.isEmpty()) {
			return Optional.empty();
		}
		int start = ALL.indexOf(from.get());
		int end = ALL.indexOf(to.get());
		return (start > end) ? Optional.empty() : Optional.of(ALL.subList(start, end + 1));
	}

}

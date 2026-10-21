package com.muhjain.school.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {boxes} of a message text, in the order they first appear.
 * Example: "नमस्ते {Parent name}, {Child name} की फ़ीस {Amount due}" → [Parent name, Child name, Amount due].
 */
public final class Placeholders {

	private static final Pattern BOX = Pattern.compile("\\{([^{}]+)\\}");

	private Placeholders() {
	}

	/**
	 * The text with every {box} that has a value replaced; a box with no value stays as it is.
	 * Example: ("{Child name} की PTM", {"Child name": "Aryan"}) → "Aryan की PTM".
	 */
	public static String fill(String text, java.util.Map<String, String> values) {
		if (text == null) {
			return null;
		}
		Matcher m = BOX.matcher(text);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String value = values.get(m.group(1));
			m.appendReplacement(out, Matcher.quoteReplacement((value == null) ? m.group() : value));
		}
		m.appendTail(out);
		return out.toString();
	}

	public static Set<String> of(String text) {
		Set<String> names = new LinkedHashSet<>();
		if (text == null) {
			return names;
		}
		Matcher m = BOX.matcher(text);
		while (m.find()) {
			names.add(m.group(1));
		}
		return names;
	}

}

package com.muhjain.school.util;

import java.time.LocalDate;
import java.util.List;

/**
 * Text for the {box} values of a notification. Hindi joins, because most parents read Hindi.
 * Examples: ["Aryan"] → "Aryan", ["Aryan", "Siya"] → "Aryan और Siya", ["Aryan", "Siya", "Kavya"] →
 * "Aryan, Siya और Kavya"; Sep and Oct 2026 → "सितंबर और अक्टूबर".
 */
public final class RecipientText {

	private static final String[] HINDI_MONTHS = { "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई",
			"अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर" };

	private RecipientText() {
	}

	/** The first word of a name. Example: "Aryan Punia" → "Aryan". Null or blank → "". */
	public static String firstName(String name) {
		if (name == null || name.isBlank()) {
			return "";
		}
		return name.strip().split("\\s+")[0];
	}

	/** "A", "A और B", "A, B और C". Empty list → "". */
	public static String join(List<String> parts) {
		if (parts.isEmpty()) {
			return "";
		}
		if (parts.size() == 1) {
			return parts.getFirst();
		}
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " और " + parts.getLast();
	}

	/** Month names in Hindi, joined. The months must be in order already. */
	public static String hindiMonths(List<LocalDate> months) {
		return join(months.stream().map(m -> HINDI_MONTHS[m.getMonthValue() - 1]).toList());
	}

}

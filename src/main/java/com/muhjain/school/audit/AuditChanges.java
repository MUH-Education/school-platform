package com.muhjain.school.audit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.muhjain.school.common.DayText;

/**
 * Collects "what changed" while a service updates a row, and turns it into the one human line and the
 * old/new details of an {@code audit_log} row.
 * <p>
 * Example:
 * <pre>
 * AuditChanges changes = new AuditChanges().field("Seats", "seats", 14, 26).field("Name", "name", "Van 4", "Van 4");
 * changes.summary()  → "Seats changed from 14 to 26."      (the unchanged name is left out)
 * changes.details()  → {"seats": {"old": 14, "new": 26}}
 * </pre>
 * Money is compared as a number, so 30300 and 30300.00 are the same.
 */
public final class AuditChanges {

	private final Map<String, Object> details = new LinkedHashMap<>();

	private final List<String> sentences = new ArrayList<>();

	/**
	 * A value that may have changed.
	 *
	 * @param label how the line starts, example "Seats"
	 * @param key the name in the details, example "seats"
	 */
	public AuditChanges field(String label, String key, Object oldValue, Object newValue) {
		if (same(oldValue, newValue)) {
			return this;
		}
		details.put(key, Map.of("old", detail(oldValue), "new", detail(newValue)));
		if (oldValue == null) {
			sentences.add(label + " set to " + text(newValue) + ".");
		}
		else if (newValue == null) {
			sentences.add(label + " removed.");
		}
		else {
			sentences.add(label + " changed from " + text(oldValue) + " to " + text(newValue) + ".");
		}
		return this;
	}

	/** Like {@link #field}, for a value that must not be written out in full, such as a phone number. */
	public AuditChanges maskedField(String label, String key, String oldValue, String newValue,
			java.util.function.UnaryOperator<String> mask) {
		return field(label, key, (oldValue == null) ? null : mask.apply(oldValue),
				(newValue == null) ? null : mask.apply(newValue));
	}

	/** A turned-on flag. Example: true → false gives "Turned off." */
	public AuditChanges active(boolean oldValue, boolean newValue) {
		if (oldValue != newValue) {
			details.put("active", Map.of("old", oldValue, "new", newValue));
			sentences.add(newValue ? "Turned on." : "Turned off.");
		}
		return this;
	}

	public boolean isEmpty() {
		return sentences.isEmpty();
	}

	/** All changes in one line, example "Seats changed from 14 to 26. Turned off." */
	public String summary() {
		return String.join(" ", sentences);
	}

	/** Old and new value of every change, for the {@code details} column. */
	public Map<String, Object> details() {
		return details;
	}

	private static boolean same(Object a, Object b) {
		if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
			return x.compareTo(y) == 0;
		}
		return Objects.equals(a, b);
	}

	// What a person reads. Example: 2026-10-28 → "28 Oct 2026", MID_BUS → "MID_BUS", 30300.00 → "30300.00".
	private static String text(Object value) {
		if (value instanceof LocalDate date) {
			return DayText.on(date);
		}
		if (value instanceof BigDecimal number) {
			return number.toPlainString();
		}
		return String.valueOf(value);
	}

	// What goes into the JSON. Enums and dates become text, so the column is plain and stable.
	private static Object detail(Object value) {
		if (value == null || value instanceof Number || value instanceof Boolean) {
			return (value instanceof BigDecimal number) ? number.toPlainString() : (value == null) ? "" : value;
		}
		if (value instanceof Enum<?> e) {
			return e.name();
		}
		return value.toString();
	}

}

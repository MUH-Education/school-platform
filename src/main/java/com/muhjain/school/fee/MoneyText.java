package com.muhjain.school.fee;

import java.math.BigDecimal;

/**
 * Rupee text for messages and the change history, with Indian digit groups.
 * Example: 125000 → "₹1,25,000", 7500.50 → "₹7,500.50", 9700.00 → "₹9,700".
 */
public final class MoneyText {

	private MoneyText() {
	}

	public static String of(BigDecimal amount) {
		BigDecimal fixed = amount.setScale(2, java.math.RoundingMode.HALF_UP);
		String sign = (fixed.signum() < 0) ? "-" : "";
		String[] parts = fixed.abs().toPlainString().split("\\.");
		String whole = parts[0];
		String grouped = whole;
		if (whole.length() > 3) {
			StringBuilder out = new StringBuilder(whole.substring(whole.length() - 3));
			String rest = whole.substring(0, whole.length() - 3);
			while (rest.length() > 2) {
				out.insert(0, rest.substring(rest.length() - 2) + ",");
				rest = rest.substring(0, rest.length() - 2);
			}
			grouped = rest + "," + out;
		}
		return sign + "₹" + grouped + (parts[1].equals("00") ? "" : "." + parts[1]);
	}

}

package com.muhjain.school.analytics;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the Analytics list as a CSV file that Excel opens (rule 10). Pure Java.
 * <ul>
 * <li>UTF-8 with a BOM, so Excel shows Hindi names correctly.</li>
 * <li>The first line is the header. Lines end with CRLF.</li>
 * <li>A cell with a comma, a quote or a line break is put in quotes; a quote inside becomes two quotes.</li>
 * <li>CSV injection: a cell that starts with {@code = + - @} (or a tab or CR) would run as a formula in Excel, so
 * it gets a {@code '} in front. Example: a name typed as {@code =HYPERLINK("http://bad")} becomes
 * {@code '=HYPERLINK(...)}.</li>
 * </ul>
 */
public final class AnalyticsCsv {

	static final String HEADER = "Name,Class,Village,Father's occupation,Bus route,School fee status,Bus fee status,"
			+ "Pending amount";

	private static final String BOM = "﻿";

	private AnalyticsCsv() {
	}

	public static byte[] write(List<AnalyticsStudentItem> rows) {
		StringBuilder out = new StringBuilder(BOM).append(HEADER).append("\r\n");
		for (AnalyticsStudentItem row : rows) {
			List<String> cells = new ArrayList<>();
			cells.add(row.name());
			cells.add(row.className());
			cells.add(row.village());
			cells.add((row.fatherOccupation() == null) ? null : row.fatherOccupation().name());
			cells.add(row.busRoute());
			cells.add((row.schoolFeeStatus() == null) ? null : row.schoolFeeStatus().name());
			cells.add((row.busFeeStatus() == null) ? null : row.busFeeStatus().name());
			cells.add(row.pendingAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
			out.append(String.join(",", cells.stream().map(AnalyticsCsv::cell).toList())).append("\r\n");
		}
		return out.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** One safe cell. Null is an empty cell. */
	static String cell(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		String safe = text;
		char first = safe.charAt(0);
		if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
			safe = "'" + safe;
		}
		if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
			safe = "\"" + safe.replace("\"", "\"\"") + "\"";
		}
		return safe;
	}

}

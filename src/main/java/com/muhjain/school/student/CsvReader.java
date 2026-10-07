package com.muhjain.school.student;

import java.util.ArrayList;
import java.util.List;

import com.muhjain.school.common.ApiException;

/**
 * A small CSV reader for the student sheet (no library, see docs/08-decisions.md). It knows what Excel writes:
 * commas, double quotes ("Singh, Ramesh"), a doubled quote inside quotes (""), a text with a line break inside
 * quotes, Windows line ends, and a byte order mark at the start.
 * Every row remembers its line number in the file, so an error can say "line 17".
 * Example: {@code Aryan,M,"14/05/2018"} → one row with 3 cells.
 */
final class CsvReader {

	/** One row. {@code line} is the line of the file where the row starts (the first line is 1). */
	record Row(int line, List<String> cells) {

		String cell(int index) {
			return (index >= 0 && index < cells.size()) ? cells.get(index).strip() : "";
		}

		boolean isBlank() {
			return cells.stream().allMatch(c -> c.isBlank());
		}

	}

	private CsvReader() {
	}

	/** @throws ApiException 400 VALIDATION if a quote is never closed */
	static List<Row> parse(String text) {
		String input = text.startsWith("﻿") ? text.substring(1) : text;
		List<Row> rows = new ArrayList<>();
		List<String> cells = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		boolean cellStarted = false;
		int line = 1;
		int rowLine = 1;
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (quoted) {
				if (c == '"') {
					if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
						cell.append('"');
						i++;
					}
					else {
						quoted = false;
					}
				}
				else {
					if (c == '\n') {
						line++;
					}
					cell.append(c);
				}
			}
			else if (c == '"' && cell.toString().isBlank()) {
				quoted = true;
				cellStarted = true;
				cell.setLength(0);
			}
			else if (c == ',') {
				cells.add(cell.toString());
				cell.setLength(0);
				cellStarted = false;
			}
			else if (c == '\n' || c == '\r') {
				if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') {
					i++;
				}
				cells.add(cell.toString());
				rows.add(new Row(rowLine, cells));
				cells = new ArrayList<>();
				cell.setLength(0);
				cellStarted = false;
				line++;
				rowLine = line;
			}
			else {
				cell.append(c);
				cellStarted = true;
			}
		}
		if (quoted) {
			throw ApiException.validation("file", "line " + rowLine + ": a quote (\") is opened but never closed");
		}
		if (cellStarted || !cells.isEmpty() || !cell.isEmpty()) {
			cells.add(cell.toString());
			rows.add(new Row(rowLine, cells));
		}
		return rows;
	}

}

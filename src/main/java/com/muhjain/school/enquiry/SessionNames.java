package com.muhjain.school.enquiry;

import java.time.LocalDate;

/**
 * The school year starts on 1 April. An enquiry is for the next session that has not started yet.
 * Example: 7 Oct 2026 → "2027-28". 3 Feb 2027 → "2027-28". 2 Apr 2027 → "2028-29".
 */
public final class SessionNames {

	private SessionNames() {
	}

	public static String nextSession(LocalDate today) {
		int startYear = today.getYear() + 1;
		return startYear + "-" + String.format("%02d", (startYear + 1) % 100);
	}

}

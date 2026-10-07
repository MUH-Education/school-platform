package com.muhjain.school.vehicle;

import java.time.LocalDate;
import java.util.Collection;

/**
 * Is a paper still good? Calculated from the last valid day and today. Never stored.
 * Example on 7 Oct 2026: valid till 28 Oct → ENDING_SOON (in 21 days). Valid till 6 Oct → ENDED.
 * Also used for a driver's licence.
 */
public enum PaperStatus {

	// The order is from worst to best. "worst()" uses it.
	ENDED, ENDING_SOON, MISSING, VALID;

	/** A paper that ends within this many days is ENDING_SOON. */
	public static final int SOON_DAYS = 30;

	/**
	 * @param validTill the last valid day, or null if no date is saved
	 * @param today the school day, from the app Clock
	 */
	public static PaperStatus of(LocalDate validTill, LocalDate today) {
		if (validTill == null) {
			return MISSING;
		}
		if (validTill.isBefore(today)) {
			return ENDED;
		}
		if (!validTill.isAfter(today.plusDays(SOON_DAYS))) {
			return ENDING_SOON;
		}
		return VALID;
	}

	/** The worst of several statuses. No papers at all → MISSING. */
	public static PaperStatus worst(Collection<PaperStatus> statuses) {
		return statuses.stream().min(Enum::compareTo).orElse(MISSING);
	}

}

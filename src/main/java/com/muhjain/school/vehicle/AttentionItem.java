package com.muhjain.school.vehicle;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * One line of "Papers and licences that need attention": ended, or ending within 30 days.
 * <ul>
 * <li>A vehicle paper: {@code kind} PAPER, {@code vehicleId}, {@code vehicleName}, {@code docType}.
 * Example: Van 4, INSURANCE, ENDING_SOON, 10 days left.</li>
 * <li>A driver's licence: {@code kind} LICENCE, {@code staffId}, {@code staffName}.
 * Example: Jagdish, ENDED, -3 days (ended 3 days ago).</li>
 * </ul>
 * The fields that do not fit the kind are null.
 */
public record AttentionItem(Kind kind, Long vehicleId, String vehicleName, DocType docType, Long staffId,
		String staffName, LocalDate validTill, PaperStatus status, long daysLeft) {

	public enum Kind {

		PAPER, LICENCE

	}

	static AttentionItem paper(Long vehicleId, String vehicleName, DocType docType, LocalDate validTill,
			LocalDate today) {
		return new AttentionItem(Kind.PAPER, vehicleId, vehicleName, docType, null, null, validTill,
				PaperStatus.of(validTill, today), ChronoUnit.DAYS.between(today, validTill));
	}

	/** For {@code StaffService}, which knows the driver. */
	public static AttentionItem licence(Long staffId, String staffName, LocalDate validTill, LocalDate today) {
		return new AttentionItem(Kind.LICENCE, null, null, null, staffId, staffName, validTill,
				PaperStatus.of(validTill, today), ChronoUnit.DAYS.between(today, validTill));
	}

}

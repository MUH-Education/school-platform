package com.muhjain.school.vehicle;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * One paper of a vehicle, with its calculated status.
 * Example: {@code { "docType": "INSURANCE", "validTill": "2026-10-28", "status": "ENDING_SOON", "daysLeft": 21 }}.
 * {@code daysLeft} is below 0 when the paper has ended (-3 = ended 3 days ago). No date saved → null.
 */
public record DocumentResponse(DocType docType, LocalDate validTill, PaperStatus status, Long daysLeft) {

	static DocumentResponse of(DocType docType, LocalDate validTill, LocalDate today) {
		Long daysLeft = (validTill != null) ? ChronoUnit.DAYS.between(today, validTill) : null;
		return new DocumentResponse(docType, validTill, PaperStatus.of(validTill, today), daysLeft);
	}

}

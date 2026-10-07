package com.muhjain.school.vehicle;

import java.time.LocalDate;

/**
 * The four paper dates (last valid day). A date left out or null means "no date saved" and removes the old one.
 * Example: {@code { "fitness": "2027-01-10", "insurance": "2026-10-28", "permit": "2027-03-31", "puc": null }}
 */
public record VehicleDocumentsRequest(LocalDate fitness, LocalDate insurance, LocalDate permit, LocalDate puc) {

	LocalDate dateOf(DocType type) {
		return switch (type) {
			case FITNESS -> fitness;
			case INSURANCE -> insurance;
			case PERMIT -> permit;
			case PUC -> puc;
		};
	}

}

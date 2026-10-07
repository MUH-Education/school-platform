package com.muhjain.school.staff;

import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Rule 9: "Who is on the vehicle on day D? The temporary row covering D wins. Else the permanent row covering D."
 * Pure Java. No Spring, no database. Give it the rows of ONE vehicle.
 * <p>
 * Example: Jagdish drives Van 4 from 1 Apr (permanent). Surender drives it 12 to 16 Oct (temporary).
 * On 14 Oct → Surender. On 17 Oct → Jagdish, and nobody changed Jagdish's row.
 */
public final class AssignmentRules {

	private AssignmentRules() {
	}

	/** The row that decides who does this duty on this day, or empty if nobody does. */
	public static Optional<VehicleAssignment> pick(Collection<VehicleAssignment> rows, Duty duty, LocalDate day) {
		VehicleAssignment temporary = null;
		VehicleAssignment permanent = null;
		for (VehicleAssignment row : rows) {
			if (row.getDuty() != duty || !row.covers(day)) {
				continue;
			}
			if (row.isTemporary()) {
				temporary = later(temporary, row);
			}
			else {
				permanent = later(permanent, row);
			}
		}
		return Optional.ofNullable((temporary != null) ? temporary : permanent);
	}

	/** One row per duty that somebody does on this day. A duty nobody does is not in the map. */
	public static Map<Duty, VehicleAssignment> crew(Collection<VehicleAssignment> rows, LocalDate day) {
		Map<Duty, VehicleAssignment> crew = new EnumMap<>(Duty.class);
		for (Duty duty : Duty.values()) {
			pick(rows, duty, day).ifPresent(row -> crew.put(duty, row));
		}
		return crew;
	}

	// The API never saves two rows of the same kind that cover the same day. If the data has them anyway
	// (someone edited the table by hand), the row that started last wins, and then the one saved last.
	private static VehicleAssignment later(VehicleAssignment current, VehicleAssignment candidate) {
		if (current == null || !candidate.getFromDate().isBefore(current.getFromDate())) {
			return candidate;
		}
		return current;
	}

}

package com.muhjain.school.staff;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who works on which vehicle, and when. Rules 6 to 12 of docs/phases/phase-2-vehicles-staff-routes.md.
 * Other features use this service, for example Routes and Bus status ask "who is on Van 4 today".
 * This is the second query of "Three queries used everywhere" in docs/03-data-model.md.
 */
@Service
public class AssignmentService {

	private final VehicleAssignmentRepository assignments;

	private final StaffRepository staff;

	private final Clock clock;

	public AssignmentService(VehicleAssignmentRepository assignments, StaffRepository staff, Clock clock) {
		this.assignments = assignments;
		this.staff = staff;
		this.clock = clock;
	}

	/**
	 * Rule 9. Example: {@code onDate(4, 2026-10-14)} → driver Surender (temporary), attendant Balwan.
	 * A vehicle that does not exist has nobody on it.
	 */
	@Transactional(readOnly = true)
	public Crew onDate(Long vehicleId, LocalDate date) {
		List<VehicleAssignment> rows = assignments.coveringDay(vehicleId, date);
		return toCrew(rows, people(rows), date);
	}

	/** Same for many vehicles with one query. Vehicles with nobody on them are in the map with {@link Crew#EMPTY}. */
	@Transactional(readOnly = true)
	public Map<Long, Crew> onDate(Collection<Long> vehicleIds, LocalDate date) {
		Map<Long, Crew> result = new HashMap<>();
		if (vehicleIds.isEmpty()) {
			return result;
		}
		List<VehicleAssignment> rows = assignments.coveringDay(vehicleIds, date);
		Map<Long, Staff> people = people(rows);
		Map<Long, List<VehicleAssignment>> byVehicle = rows.stream()
			.collect(Collectors.groupingBy(VehicleAssignment::getVehicleId));
		for (Long vehicleId : vehicleIds) {
			result.put(vehicleId, toCrew(byVehicle.getOrDefault(vehicleId, List.of()), people, date));
		}
		return result;
	}

	/** The school day, in the school zone. */
	LocalDate today() {
		return LocalDate.now(clock);
	}

	// The rows of ONE vehicle that cover the day.
	private Crew toCrew(List<VehicleAssignment> rows, Map<Long, Staff> people, LocalDate date) {
		Map<Duty, VehicleAssignment> crew = AssignmentRules.crew(rows, date);
		return new Crew(member(crew.get(Duty.DRIVER), people), member(crew.get(Duty.ATTENDANT), people),
				member(crew.get(Duty.HELPER), people));
	}

	private static CrewMember member(VehicleAssignment row, Map<Long, Staff> people) {
		if (row == null) {
			return null;
		}
		Staff person = people.get(row.getStaffId());
		return new CrewMember(row.getStaffId(), person.getName(), person.getPhone(), row.isTemporary());
	}

	// The staff rows of these assignments, by id, so names and phones need only one query.
	private Map<Long, Staff> people(List<VehicleAssignment> rows) {
		Set<Long> ids = rows.stream().map(VehicleAssignment::getStaffId).collect(Collectors.toSet());
		return staff.findAllById(ids).stream().collect(Collectors.toMap(Staff::getId, s -> s));
	}

}

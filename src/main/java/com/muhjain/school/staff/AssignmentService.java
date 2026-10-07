package com.muhjain.school.staff;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.DayText;
import com.muhjain.school.vehicle.VehicleResponse;
import com.muhjain.school.vehicle.VehicleService;
import org.springframework.http.HttpStatus;
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

	// "No end date" for the overlap query. Not LocalDate.MAX, because PostgreSQL cannot hold that year.
	private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

	private final StaffRepository staff;

	private final VehicleService vehicleService;

	private final Clock clock;

	public AssignmentService(VehicleAssignmentRepository assignments, StaffRepository staff,
			VehicleService vehicleService, Clock clock) {
		this.assignments = assignments;
		this.staff = staff;
		this.vehicleService = vehicleService;
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

	/**
	 * Rules 6 to 8 and 10 to 12: put a person on a duty of a vehicle.
	 * <ul>
	 * <li>Permanent (rule 7): the old permanent row is closed on the day before {@code fromDate}. The new row has
	 * no end. Example: from 1 Nov, Surender replaces Jagdish → Jagdish's row ends on 31 Oct.</li>
	 * <li>Temporary (rule 8): one new row with both dates. The permanent row is not touched.
	 * Example: Surender, 12 to 16 Oct. On 17 Oct Jagdish drives again.</li>
	 * </ul>
	 *
	 * @param actorId the logged-in user, saved as {@code created_by}
	 * @throws ApiException 400 VALIDATION (dates, unknown person), 404 NOT_FOUND (vehicle),
	 * 409 VEHICLE_INACTIVE, STAFF_INACTIVE, WRONG_STAFF_TYPE, LICENCE_ENDED, STAFF_BUSY,
	 * FROM_DATE_TOO_EARLY, TEMPORARY_OVERLAP
	 */
	@Transactional
	public AssignmentResponse change(Long vehicleId, ChangeAssignmentRequest request, Long actorId) {
		boolean temporary = Boolean.TRUE.equals(request.temporary());
		LocalDate from = request.fromDate();
		LocalDate to = request.toDate();
		checkDates(temporary, from, to);

		// Lock the vehicle first, then the person. Always this order, so two changes cannot block each other.
		VehicleResponse vehicle = vehicleService.lockActive(vehicleId);
		Staff person = staff.findByIdForUpdate(request.staffId())
			.orElseThrow(() -> ApiException.validation("staffId", "does not exist"));
		Duty duty = request.duty();
		checkPerson(person, duty, from);
		checkNotBusy(person, vehicleId, from, to);

		VehicleAssignment row = new VehicleAssignment(vehicleId, person.getId(), duty, from, temporary ? to : null,
				temporary, request.reason(), actorId);
		if (temporary) {
			checkNoOtherTemporary(vehicle.name(), vehicleId, duty, from, to);
		}
		else {
			closeOldPermanentRow(vehicle.name(), vehicleId, duty, from);
		}
		row = assignments.saveAndFlush(row);
		return AssignmentResponse.of(row, person.getName(), clock.getZone());
	}

	// Rule 6. A temporary change needs an end day. A permanent change has none.
	private static void checkDates(boolean temporary, LocalDate from, LocalDate to) {
		if (temporary && to == null) {
			throw ApiException.validation("toDate", "is required for a temporary change");
		}
		if (!temporary && to != null) {
			throw ApiException.validation("toDate", "is only for a temporary change");
		}
		if (temporary && to.isBefore(from)) {
			throw ApiException.validation("toDate", "must not be before fromDate");
		}
	}

	// Rules 11 and 12, and a person who is turned off.
	private static void checkPerson(Staff person, Duty duty, LocalDate from) {
		if (!person.isActive()) {
			throw new ApiException(HttpStatus.CONFLICT, "STAFF_INACTIVE",
					person.getName() + " is turned off. Turn them on first.");
		}
		if (person.getStaffType() != duty.requiredType()) {
			throw new ApiException(HttpStatus.CONFLICT, "WRONG_STAFF_TYPE", person.getName() + " works as "
					+ person.getStaffType() + ". The " + duty + " duty needs a " + duty.requiredType() + ".");
		}
		if (duty == Duty.DRIVER && person.getLicenceValidTill().isBefore(from)) {
			throw new ApiException(HttpStatus.CONFLICT, "LICENCE_ENDED", person.getName() + "'s licence ended on "
					+ DayText.on(person.getLicenceValidTill()) + ".");
		}
	}

	// Rule 10. One person is on one duty of one vehicle on a day. Any row of the person that touches
	// the new days is a clash, also a row of the same vehicle.
	// Example: "Rajpal drives Van 1 on these days."
	private void checkNotBusy(Staff person, Long vehicleId, LocalDate from, LocalDate to) {
		List<VehicleAssignment> clashes = assignments.ofStaffBetween(person.getId(), from,
				(to != null) ? to : FAR_FUTURE);
		if (clashes.isEmpty()) {
			return;
		}
		VehicleAssignment other = clashes.getFirst();
		String vehicle = vehicleService.names(List.of(other.getVehicleId())).get(other.getVehicleId());
		String what = switch (other.getDuty()) {
			case DRIVER -> "drives " + vehicle;
			case ATTENDANT -> "is the attendant of " + vehicle;
			case HELPER -> "is the helper of " + vehicle;
		};
		throw new ApiException(HttpStatus.CONFLICT, "STAFF_BUSY", person.getName() + " " + what + " on these days.");
	}

	// Rule 8. Two replacements for the same duty on the same day would be unclear. Not allowed.
	private void checkNoOtherTemporary(String vehicleName, Long vehicleId, Duty duty, LocalDate from, LocalDate to) {
		List<VehicleAssignment> clashes = assignments.ofVehicleDutyBetween(vehicleId, duty, true, from, to);
		if (!clashes.isEmpty()) {
			VehicleAssignment other = clashes.getFirst();
			String name = staff.findById(other.getStaffId()).orElseThrow().getName();
			throw new ApiException(HttpStatus.CONFLICT, "TEMPORARY_OVERLAP", name + " already covers the " + duty
					+ " duty of " + vehicleName + " from " + DayText.range(other.getFromDate(), other.getToDate())
					+ ".");
		}
	}

	// Rule 7. The new person starts on `from`. The person before ends on the day before.
	private void closeOldPermanentRow(String vehicleName, Long vehicleId, Duty duty, LocalDate from) {
		List<VehicleAssignment> permanent = assignments
			.findByVehicleIdAndDutyAndTemporaryFalseOrderByFromDateAscIdAsc(vehicleId, duty);
		if (permanent.isEmpty()) {
			return;
		}
		VehicleAssignment latest = permanent.getLast();
		if (!latest.getFromDate().isBefore(from)) {
			throw new ApiException(HttpStatus.CONFLICT, "FROM_DATE_TOO_EARLY", vehicleName + " has had a " + duty
					+ " since " + DayText.on(latest.getFromDate()) + ". Choose a day after that.");
		}
		// Every row now starts before `from`, and rows of one duty do not overlap. So at most one covers `from`.
		permanent.stream()
			.filter(row -> row.getToDate() == null || !row.getToDate().isBefore(from))
			.findFirst()
			.ifPresent(row -> row.setToDate(from.minusDays(1)));
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

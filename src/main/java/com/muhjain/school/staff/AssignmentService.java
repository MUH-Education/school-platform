package com.muhjain.school.staff;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditService;
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

	private final AuditService auditService;

	private final Clock clock;

	public AssignmentService(VehicleAssignmentRepository assignments, StaffRepository staff,
			VehicleService vehicleService, AuditService auditService, Clock clock) {
		this.assignments = assignments;
		this.staff = staff;
		this.vehicleService = vehicleService;
		this.auditService = auditService;
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
	 * Everybody who worked on this vehicle, newest first (by first day). Temporary rows are included.
	 *
	 * @throws ApiException 404 NOT_FOUND
	 */
	@Transactional(readOnly = true)
	public List<AssignmentResponse> history(Long vehicleId) {
		vehicleService.requireExists(vehicleId);
		List<VehicleAssignment> rows = assignments.findByVehicleIdOrderByFromDateDescIdDesc(vehicleId);
		Map<Long, Staff> people = people(rows);
		return rows.stream()
			.map(r -> AssignmentResponse.of(r, people.get(r.getStaffId()).getName(), clock.getZone()))
			.toList();
	}

	/**
	 * The vehicle where this person is the ATTENDANT on this day. Rule 9 applies: a permanent attendant whose
	 * duty is covered by a temporary replacement that day is not on the vehicle, and the replacement is.
	 * Example: Balwan is the attendant of Van 4 → 4. On 14 Oct Hari replaces him → Balwan: empty, Hari: 4.
	 * A person who is turned off is on no vehicle.
	 */
	@Transactional(readOnly = true)
	public Optional<Long> attendantVehicleOn(Long staffId, LocalDate day) {
		if (staff.findById(staffId).filter(Staff::isActive).isEmpty()) {
			return Optional.empty();
		}
		return assignments.ofStaffCoveringDay(staffId, day)
			.stream()
			.filter(row -> row.getDuty() == Duty.ATTENDANT)
			.map(VehicleAssignment::getVehicleId)
			.filter(vehicleId -> AssignmentRules.pick(assignments.coveringDay(vehicleId, day), Duty.ATTENDANT, day)
				.map(VehicleAssignment::getStaffId)
				.filter(staffId::equals)
				.isPresent())
			.findFirst();
	}

	/**
	 * Where each person works on one day. A person who is on no vehicle that day is not in the map.
	 * A replaced permanent person is not on the vehicle that day either (rule 9).
	 */
	@Transactional(readOnly = true)
	public Map<Long, Placement> placesOn(LocalDate day) {
		Map<Long, List<VehicleAssignment>> byVehicle = assignments.coveringDay(day)
			.stream()
			.collect(Collectors.groupingBy(VehicleAssignment::getVehicleId));
		Map<Long, String> vehicleNames = vehicleService.names(byVehicle.keySet());
		Map<Long, Placement> places = new HashMap<>();
		byVehicle.forEach((vehicleId, rows) -> AssignmentRules.crew(rows, day)
			.forEach((duty, row) -> places.put(row.getStaffId(),
					new Placement(vehicleId, vehicleNames.get(vehicleId), duty, row.isTemporary()))));
		return places;
	}

	/** Same for one person. */
	@Transactional(readOnly = true)
	public Placement placeOf(Long staffId, LocalDate day) {
		return placesOn(day).get(staffId);
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
		// "Who was on the duty before?" Needed for the change history.
		Optional<VehicleAssignment> previous;
		if (temporary) {
			checkNoOtherTemporary(vehicle.name(), vehicleId, duty, from, to);
			previous = AssignmentRules.pick(assignments.coveringDay(vehicleId, from), duty, from);
		}
		else {
			previous = closeOldPermanentRow(vehicle.name(), vehicleId, duty, from);
		}
		row = assignments.saveAndFlush(row);
		audit(row, person, previous);
		return AssignmentResponse.of(row, person.getName(), clock.getZone());
	}

	// Task 2.15. Example: "Driver changed from Jagdish to Surender, 12 to 16 Oct" (temporary)
	// or "Driver changed from Jagdish to Surender from 1 Nov 2026" (permanent).
	private void audit(VehicleAssignment row, Staff person, Optional<VehicleAssignment> previous) {
		String oldName = previous.map(p -> staff.findById(p.getStaffId()).orElseThrow().getName()).orElse(null);
		String duty = row.getDuty().name().charAt(0) + row.getDuty().name().substring(1).toLowerCase(Locale.ROOT);
		String when = row.isTemporary() ? ", " + DayText.range(row.getFromDate(), row.getToDate())
				: " from " + DayText.on(row.getFromDate());
		String summary = (oldName == null) ? duty + " set to " + person.getName() + when
				: duty + " changed from " + oldName + " to " + person.getName() + when;
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("duty", row.getDuty().name());
		details.put("staffId", person.getId());
		details.put("staffName", person.getName());
		details.put("previousStaffId", previous.map(VehicleAssignment::getStaffId).orElse(null));
		details.put("previousStaffName", oldName);
		details.put("fromDate", row.getFromDate().toString());
		details.put("toDate", (row.getToDate() != null) ? row.getToDate().toString() : null);
		details.put("temporary", row.isTemporary());
		details.put("reason", (row.getReason() != null) ? row.getReason().name() : null);
		auditService.record("VEHICLE", row.getVehicleId(), AuditAction.UPDATED, summary, details);
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
	// Returns the row that was closed, or empty if nobody was on the duty.
	private Optional<VehicleAssignment> closeOldPermanentRow(String vehicleName, Long vehicleId, Duty duty,
			LocalDate from) {
		List<VehicleAssignment> permanent = assignments
			.findByVehicleIdAndDutyAndTemporaryFalseOrderByFromDateAscIdAsc(vehicleId, duty);
		if (permanent.isEmpty()) {
			return Optional.empty();
		}
		VehicleAssignment latest = permanent.getLast();
		if (!latest.getFromDate().isBefore(from)) {
			throw new ApiException(HttpStatus.CONFLICT, "FROM_DATE_TOO_EARLY", vehicleName + " has had a " + duty
					+ " since " + DayText.on(latest.getFromDate()) + ". Choose a day after that.");
		}
		// Every row now starts before `from`, and rows of one duty do not overlap. So at most one covers `from`.
		Optional<VehicleAssignment> closing = permanent.stream()
			.filter(row -> row.getToDate() == null || !row.getToDate().isBefore(from))
			.findFirst();
		closing.ifPresent(row -> row.setToDate(from.minusDays(1)));
		return closing;
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

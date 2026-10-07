package com.muhjain.school.staff;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditChanges;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.NameKeys;
import com.muhjain.school.common.PhoneNumbers;
import com.muhjain.school.vehicle.AttentionItem;
import com.muhjain.school.vehicle.PaperStatus;
import com.muhjain.school.vehicle.VehicleService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drivers, attendants and helpers. Rules 4 and 5 of docs/phases/phase-2-vehicles-staff-routes.md.
 * Other features use this service, never {@link StaffRepository}.
 */
@Service
public class StaffService {

	static final String ENTITY = "STAFF";

	private final StaffRepository staff;

	private final VehicleAssignmentRepository assignments;

	private final VehicleService vehicleService;

	private final AssignmentService assignmentService;

	private final AuditService auditService;

	private final Clock clock;

	public StaffService(StaffRepository staff, VehicleAssignmentRepository assignments,
			VehicleService vehicleService, AssignmentService assignmentService, AuditService auditService,
			Clock clock) {
		this.staff = staff;
		this.assignments = assignments;
		this.vehicleService = vehicleService;
		this.assignmentService = assignmentService;
		this.auditService = auditService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<StaffResponse> list() {
		LocalDate today = today();
		Map<Long, Placement> places = assignmentService.placesOn(today);
		return staff.findAllByOrderByIdAsc()
			.stream()
			.map(s -> StaffResponse.of(s, today, places.get(s.getId())))
			.toList();
	}

	/**
	 * The type of a staff member, or empty if there is no such row. Used by the user feature to check that an
	 * ATTENDANT user points at an ATTENDANT staff member.
	 */
	@Transactional(readOnly = true)
	public Optional<StaffType> findType(Long staffId) {
		return staff.findById(staffId).map(Staff::getStaffType);
	}

	/** Licences of turned-on drivers that have ended or end within 30 days, the most urgent first. */
	@Transactional(readOnly = true)
	public List<AttentionItem> licenceAttention(LocalDate today) {
		return staff
			.findByStaffTypeAndActiveTrueAndLicenceValidTillLessThanEqualOrderByLicenceValidTillAscIdAsc(
					StaffType.DRIVER, today.plusDays(PaperStatus.SOON_DAYS))
			.stream()
			.map(s -> AttentionItem.licence(s.getId(), s.getName(), s.getLicenceValidTill(), today))
			.toList();
	}

	/** @throws ApiException 404 NOT_FOUND */
	@Transactional(readOnly = true)
	public StaffResponse get(Long id) {
		return toResponse(find(id));
	}

	/**
	 * Rule 4. A DRIVER needs a licence number and an end date. For others both are ignored.
	 *
	 * @throws ApiException 400 VALIDATION for a bad phone, or a driver with no licence
	 */
	@Transactional
	public StaffResponse create(CreateStaffRequest request) {
		Staff person = new Staff(NameKeys.tidy(request.name()), PhoneNumbers.normalize(request.phone()),
				request.staffType());
		applyLicence(person, request.licenceNo(), request.licenceValidTill());
		person = staff.saveAndFlush(person);
		auditService.record(ENTITY, person.getId(), AuditAction.CREATED,
				"Staff " + person.getName() + " added as " + person.getStaffType(), null);
		return toResponse(person);
	}

	/**
	 * Rule 5 also holds for {@code active: false}. The type cannot change once the person has worked a duty,
	 * because the old rows would then say "driver" about a person who is no driver.
	 *
	 * @throws ApiException 404 NOT_FOUND, 400 VALIDATION, 409 STAFF_ASSIGNED, 409 STAFF_TYPE_IN_USE
	 */
	@Transactional
	public StaffResponse update(Long id, UpdateStaffRequest request) {
		Staff person = find(id);
		if (person.isActive() && !request.active()) {
			checkNotOnAVehicle(person);
		}
		if (person.getStaffType() != request.staffType() && assignments.existsByStaffId(id)) {
			throw new ApiException(HttpStatus.CONFLICT, "STAFF_TYPE_IN_USE", person.getName() + " has worked as "
					+ person.getStaffType() + " on a vehicle. The type cannot change.");
		}
		AuditChanges changes = new AuditChanges();
		String name = NameKeys.tidy(request.name());
		String phone = PhoneNumbers.normalize(request.phone());
		changes.field("Name", "name", person.getName(), name)
			.maskedField("Phone", "phone", person.getPhone(), phone, PhoneNumbers::mask)
			.field("Type", "staffType", person.getStaffType(), request.staffType());
		String oldLicenceNo = person.getLicenceNo();
		LocalDate oldLicenceTill = person.getLicenceValidTill();
		person.setName(name);
		person.setPhone(phone);
		person.setStaffType(request.staffType());
		applyLicence(person, request.licenceNo(), request.licenceValidTill());
		changes.field("Licence number", "licenceNo", oldLicenceNo, person.getLicenceNo())
			.field("Licence valid till", "licenceValidTill", oldLicenceTill, person.getLicenceValidTill())
			.active(person.isActive(), request.active());
		person.setActive(request.active());
		person = staff.saveAndFlush(person);
		if (!changes.isEmpty()) {
			auditService.record(ENTITY, id, AuditAction.UPDATED, changes.summary(), changes.details());
		}
		return toResponse(person);
	}

	/**
	 * Turn off. People are never deleted (B10). Turning off a person who is already off changes nothing.
	 * Rule 5: a person who is on a vehicle today or later cannot be turned off.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 STAFF_ASSIGNED
	 */
	@Transactional
	public void turnOff(Long id) {
		Staff person = find(id);
		if (person.isActive()) {
			checkNotOnAVehicle(person);
			person.setActive(false);
			AuditChanges changes = new AuditChanges().active(true, false);
			auditService.record(ENTITY, id, AuditAction.UPDATED, changes.summary(), changes.details());
		}
	}

	// One person with where they work today.
	private StaffResponse toResponse(Staff person) {
		LocalDate today = today();
		return StaffResponse.of(person, today, assignmentService.placeOf(person.getId(), today));
	}

	/** The school day, in the school zone. */
	LocalDate today() {
		return LocalDate.now(clock);
	}

	private Staff find(Long id) {
		return staff.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This person does not exist."));
	}

	// Rule 5. Example: "Jagdish drives Van 4 from 1 Apr 2026. Change the driver of Van 4 first."
	private void checkNotOnAVehicle(Staff person) {
		List<VehicleAssignment> rows = assignments.onOrAfter(person.getId(), today());
		if (!rows.isEmpty()) {
			VehicleAssignment row = rows.getFirst();
			String vehicle = vehicleService.names(List.of(row.getVehicleId())).get(row.getVehicleId());
			throw new ApiException(HttpStatus.CONFLICT, "STAFF_ASSIGNED", person.getName() + " works on " + vehicle
					+ " as " + row.getDuty() + ". Change who works there first.");
		}
	}

	// Rule 4: only a DRIVER has a licence. For everyone else the two fields are cleared.
	private static void applyLicence(Staff person, String licenceNo, LocalDate licenceValidTill) {
		if (person.getStaffType() != StaffType.DRIVER) {
			person.setLicenceNo(null);
			person.setLicenceValidTill(null);
			return;
		}
		if (licenceNo == null || licenceNo.isBlank()) {
			throw ApiException.validation("licenceNo", "is required for a driver");
		}
		if (licenceValidTill == null) {
			throw ApiException.validation("licenceValidTill", "is required for a driver");
		}
		person.setLicenceNo(NameKeys.tidy(licenceNo));
		person.setLicenceValidTill(licenceValidTill);
	}

}

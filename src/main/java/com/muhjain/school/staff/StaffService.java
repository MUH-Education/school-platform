package com.muhjain.school.staff;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditChanges;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.NameKeys;
import com.muhjain.school.common.PhoneNumbers;
import com.muhjain.school.student.ClassNames;
import com.muhjain.school.vehicle.AttentionItem;
import com.muhjain.school.vehicle.PaperStatus;
import com.muhjain.school.vehicle.VehicleService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every employee of the school: drivers, attendants, helpers and teachers. Rules 4 and 5 of
 * docs/phases/phase-2-vehicles-staff-routes.md, and the rules of docs/phases/phase-10-staff-and-teachers.md.
 * Other features use this service, never {@link StaffRepository}.
 * The salary is not here: see {@link StaffSalaryService}.
 */
@Service
public class StaffService {

	static final String ENTITY = "STAFF";

	private final StaffRepository staff;

	private final TeacherProfileRepository teacherProfiles;

	private final VehicleAssignmentRepository assignments;

	private final VehicleService vehicleService;

	private final AssignmentService assignmentService;

	private final AuditService auditService;

	private final Clock clock;

	public StaffService(StaffRepository staff, TeacherProfileRepository teacherProfiles,
			VehicleAssignmentRepository assignments, VehicleService vehicleService,
			AssignmentService assignmentService, AuditService auditService, Clock clock) {
		this.staff = staff;
		this.teacherProfiles = teacherProfiles;
		this.assignments = assignments;
		this.vehicleService = vehicleService;
		this.assignmentService = assignmentService;
		this.auditService = auditService;
		this.clock = clock;
	}

	/**
	 * Everybody, or only some types. The Vehicles and staff screen asks for DRIVER, ATTENDANT and HELPER, so
	 * teachers do not appear there.
	 *
	 * @param types the types wanted, or empty for everybody
	 */
	@Transactional(readOnly = true)
	public List<StaffResponse> list(Set<StaffType> types) {
		LocalDate today = today();
		Map<Long, Placement> places = assignmentService.placesOn(today);
		List<Staff> people = (types == null || types.isEmpty()) ? staff.findAllByOrderByIdAsc()
				: staff.findByStaffTypeInOrderByIdAsc(types);
		Map<Long, TeacherProfile> profiles = profilesOf(people);
		return people.stream()
			.map(s -> StaffResponse.of(s, today, places.get(s.getId()), profiles.get(s.getId())))
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
	 * A TEACHER always gets a {@code teacher_profile} row, even when {@code teaching} is left out.
	 *
	 * @throws ApiException 400 VALIDATION for a bad phone or a driver with no licence, 400 NOT_A_TEACHER,
	 * 409 CLASS_TEACHER_TAKEN
	 */
	@Transactional
	public StaffResponse create(CreateStaffRequest request) {
		Staff person = new Staff(NameKeys.tidy(request.name()), PhoneNumbers.normalize(request.phone()),
				request.staffType());
		applyLicence(person, request.licenceNo(), request.licenceValidTill());
		applyDetails(person, request.details());
		person = staff.saveAndFlush(person);
		checkTeachingFits(person, request.teaching());
		if (person.getStaffType() == StaffType.TEACHER) {
			saveTeaching(person, request.teaching());
		}
		auditService.record(ENTITY, person.getId(), AuditAction.CREATED,
				"Staff " + person.getName() + " added as " + person.getStaffType(), null);
		return toResponse(person);
	}

	/**
	 * Rule 5 also holds for {@code active: false}. The type cannot change once the person has worked a duty,
	 * because the old rows would then say "driver" about a person who is no driver.
	 * Changing the type away from TEACHER deletes the teaching file. Changing it to TEACHER starts an empty one.
	 *
	 * @throws ApiException 404 NOT_FOUND, 400 VALIDATION, 400 NOT_A_TEACHER, 409 STAFF_ASSIGNED,
	 * 409 STAFF_TYPE_IN_USE, 409 CLASS_TEACHER_TAKEN
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
		EmployeeDetails oldDetails = EmployeeDetails.of(person);
		person.setName(name);
		person.setPhone(phone);
		person.setStaffType(request.staffType());
		applyLicence(person, request.licenceNo(), request.licenceValidTill());
		applyDetails(person, request.details());
		changes.field("Licence number", "licenceNo", oldLicenceNo, person.getLicenceNo())
			.field("Licence valid till", "licenceValidTill", oldLicenceTill, person.getLicenceValidTill());
		recordDetailChanges(changes, oldDetails, EmployeeDetails.of(person));
		changes.active(person.isActive(), request.active());
		person.setActive(request.active());
		person = staff.saveAndFlush(person);
		checkTeachingFits(person, request.teaching());
		if (person.getStaffType() == StaffType.TEACHER) {
			saveTeaching(person, request.teaching());
		}
		else {
			// A person who was a teacher until now loses the teaching file.
			teacherProfiles.findById(id).ifPresent(teacherProfiles::delete);
		}
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

	/** Used by {@link StaffSalaryService} to check that the person exists, and for the audit sentence. */
	@Transactional(readOnly = true)
	public String nameOf(Long id) {
		return find(id).getName();
	}

	// One person with where they work today, and the teaching file if they are a teacher.
	private StaffResponse toResponse(Staff person) {
		LocalDate today = today();
		TeacherProfile profile = (person.getStaffType() == StaffType.TEACHER)
				? teacherProfiles.findById(person.getId()).orElse(null) : null;
		return StaffResponse.of(person, today, assignmentService.placeOf(person.getId(), today), profile);
	}

	// One query for the whole list, so a list of 40 teachers is still one SELECT.
	private Map<Long, TeacherProfile> profilesOf(List<Staff> people) {
		List<Long> teacherIds = people.stream()
			.filter(s -> s.getStaffType() == StaffType.TEACHER)
			.map(Staff::getId)
			.toList();
		if (teacherIds.isEmpty()) {
			return Map.of();
		}
		return teacherProfiles.findByStaffIdIn(teacherIds)
			.stream()
			.collect(Collectors.toMap(TeacherProfile::getStaffId, Function.identity()));
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

	// The office file. PUT sends the whole person, so a field that is left out is cleared.
	private static void applyDetails(Staff person, EmployeeDetails details) {
		EmployeeDetails wanted = (details == null) ? EmployeeDetails.EMPTY : details;
		if ((wanted.idProofType() == null) != (wanted.idProofLast4() == null)) {
			throw ApiException.validation("idProofType", "and idProofLast4 go together: send both or neither");
		}
		person.setJoinedOn(wanted.joinedOn());
		person.setDateOfBirth(wanted.dateOfBirth());
		person.setGender(wanted.gender());
		person.setAddress(blankToNull(NameKeys.tidy(wanted.address())));
		person.setEmergencyPhone(
				(wanted.emergencyPhone() == null || wanted.emergencyPhone().isBlank()) ? null
						: PhoneNumbers.normalize(wanted.emergencyPhone()));
		person.setIdProofType(wanted.idProofType());
		person.setIdProofLast4(wanted.idProofLast4());
	}

	// Only a teacher may send a teaching file. Example: a HELPER with "teaching" → 400 NOT_A_TEACHER.
	private static void checkTeachingFits(Staff person, Teaching teaching) {
		if (person.getStaffType() != StaffType.TEACHER && teaching != null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_TEACHER",
					person.getName() + " is a " + person.getStaffType() + ", so there is no teaching file.");
		}
	}

	/**
	 * The teaching file of a TEACHER. A teacher always has a row, even when {@code teaching} was left out.
	 * Rule: one class has at most one class teacher.
	 */
	private void saveTeaching(Staff person, Teaching teaching) {
		Teaching wanted = (teaching == null) ? Teaching.EMPTY : teaching;
		String className = checkedClass(wanted.classTeacherOf(), person.getId());
		TeacherProfile profile = teacherProfiles.findById(person.getId())
			.orElseGet(() -> new TeacherProfile(person.getId(), clock.instant()));
		profile.change(blankToNull(NameKeys.tidy(wanted.qualification())),
				blankToNull(NameKeys.tidy(wanted.subjects())), className, clock.instant());
		teacherProfiles.saveAndFlush(profile);
	}

	// 400 if it is not a class of this school, 409 if another teacher already has that class.
	private String checkedClass(String text, Long staffId) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String className = ClassNames.parse(text)
			.orElseThrow(() -> ApiException.validation("teaching.classTeacherOf", "is not a class of this school"));
		teacherProfiles.findByClassTeacherOf(className)
			.filter(other -> !other.getStaffId().equals(staffId))
			.ifPresent(other -> {
				throw new ApiException(HttpStatus.CONFLICT, "CLASS_TEACHER_TAKEN",
						find(other.getStaffId()).getName() + " is already the class teacher of " + className + ".");
			});
		return className;
	}

	// The audit sentence for the office file. The emergency phone is masked, like every phone in a log.
	private static void recordDetailChanges(AuditChanges changes, EmployeeDetails old, EmployeeDetails now) {
		changes.field("Joined on", "joinedOn", old.joinedOn(), now.joinedOn())
			.field("Date of birth", "dateOfBirth", old.dateOfBirth(), now.dateOfBirth())
			.field("Gender", "gender", old.gender(), now.gender())
			.field("Address", "address", old.address(), now.address())
			.maskedField("Emergency phone", "emergencyPhone", old.emergencyPhone(), now.emergencyPhone(),
					PhoneNumbers::mask)
			.field("ID proof", "idProofType", old.idProofType(), now.idProofType())
			.field("ID last 4", "idProofLast4", old.idProofLast4(), now.idProofLast4());
	}

	private static String blankToNull(String text) {
		return (text == null || text.isBlank()) ? null : text;
	}

}

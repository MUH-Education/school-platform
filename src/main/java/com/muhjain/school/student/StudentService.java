package com.muhjain.school.student;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditChanges;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.NameKeys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Looking at a student and changing the details. Rules 15 and 16 of docs/phases/phase-3-students-admission.md.
 * Other features use this service, never {@link StudentRepository}.
 * Example: Aryan, A-2026-118, class 3, section B. The clerk moves him to section A.
 * The history line is "Section changed from B to A."
 */
@Service
public class StudentService {

	static final String ENTITY = "STUDENT";

	private final StudentRepository students;

	private final GuardianService guardianService;

	private final TransportEnrolmentService transportService;

	private final AuditService auditService;

	private final Clock clock;

	public StudentService(StudentRepository students, GuardianService guardianService,
			TransportEnrolmentService transportService, AuditService auditService, Clock clock) {
		this.students = students;
		this.guardianService = guardianService;
		this.transportService = transportService;
		this.auditService = auditService;
		this.clock = clock;
	}

	/** The full profile. @throws ApiException 404 NOT_FOUND */
	@Transactional(readOnly = true)
	public StudentResponse get(Long id) {
		return toResponse(find(id));
	}

	/**
	 * Rule 15 and 16. The admission number never changes. A save that changes nothing writes no audit row.
	 *
	 * @throws ApiException 404 NOT_FOUND, 400 VALIDATION (class, date of birth)
	 */
	@Transactional
	public StudentResponse update(Long id, UpdateStudentRequest request) {
		Student student = find(id);
		String className = ClassNames.parse(request.className())
			.orElseThrow(() -> ApiException.validation("className", "must be Nursery, LKG, UKG or 1 to 12"));
		if (request.dob().isAfter(LocalDate.now(clock))) {
			throw ApiException.validation("dob", "cannot be in the future");
		}
		String name = NameKeys.tidy(request.name());
		String section = tidySection(request.section());
		String village = NameKeys.tidy(request.village());
		String address = tidyOrNull(request.address());

		AuditChanges changes = new AuditChanges().field("Name", "name", student.getName(), name)
			.field("Date of birth", "dob", student.getDob(), request.dob())
			.field("Gender", "gender", student.getGender(), request.gender())
			.field("Class", "className", student.getClassName(), className)
			.field("Section", "section", student.getSection(), section)
			.field("Village", "village", student.getVillage(), village)
			.field("Address", "address", student.getAddress(), address)
			.field("Father's occupation", "fatherOccupation", student.getFatherOccupation(),
					request.fatherOccupation());

		student.setName(name);
		student.setDob(request.dob());
		student.setGender(request.gender());
		student.setClassName(className);
		student.setSection(section);
		student.setVillage(village);
		student.setAddress(address);
		student.setFatherOccupation(request.fatherOccupation());
		students.save(student);
		if (!changes.isEmpty()) {
			auditService.record(ENTITY, id, AuditAction.UPDATED, changes.summary(), changes.details());
		}
		return toResponse(student);
	}

	// ---- helpers used by the other classes of this package ----

	Student find(Long id) {
		return students.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student does not exist."));
	}

	StudentResponse toResponse(Student s) {
		List<GuardianResponse> guardians = guardianService.list(s.getId());
		EnrolmentResponse bus = transportService.current(s.getId(), LocalDate.now(clock)).orElse(null);
		return new StudentResponse(s.getId(), s.getAdmissionNo(), s.getName(), s.getDob(), s.getGender(),
				s.getClassName(), s.getSection(), s.getVillage(), s.getAddress(), s.getFatherOccupation(),
				s.getStatus(), s.getJoinedOn(), s.getLeftOn(), s.getPhotoKey() != null, guardians, bus);
	}

	static String tidySection(String section) {
		String tidy = tidyOrNull(section);
		return (tidy == null) ? null : tidy.toUpperCase(Locale.ROOT);
	}

	static String tidyOrNull(String text) {
		String tidy = NameKeys.tidy(text);
		return (tidy == null || tidy.isEmpty()) ? null : tidy;
	}

}

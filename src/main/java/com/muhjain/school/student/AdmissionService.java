package com.muhjain.school.student;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.NameKeys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A new admission. Rules 1 to 4 of docs/phases/phase-3-students-admission.md.
 * <p>
 * Everything happens in <b>one transaction</b>: the number, the student, the parents' phones and the bus.
 * If any part fails, nothing is saved, not even the admission number.
 * Example: the clerk picks a stop that is not on the chosen route. The answer is 400 STOP_NOT_ON_ROUTE and the
 * database has no new student, no new guardian and no used number.
 */
@Service
public class AdmissionService {

	private final StudentRepository students;

	private final AdmissionNumberService numbers;

	private final GuardianService guardianService;

	private final TransportEnrolmentService transportService;

	private final AuditService auditService;

	private final Clock clock;

	public AdmissionService(StudentRepository students, AdmissionNumberService numbers,
			GuardianService guardianService, TransportEnrolmentService transportService, AuditService auditService,
			Clock clock) {
		this.students = students;
		this.numbers = numbers;
		this.guardianService = guardianService;
		this.transportService = transportService;
		this.auditService = auditService;
		this.clock = clock;
	}

	/**
	 * @param userId the logged-in user, from the token
	 * @throws ApiException 400 VALIDATION (class, date, phone, no phone at all, bad route or stop), 400
	 * STOP_NOT_ON_ROUTE, 409 PHONE_ALREADY_LINKED (the same phone typed twice), 409 FROM_DATE_TOO_EARLY
	 */
	@Transactional
	public AdmissionResponse admit(AdmissionRequest request, Long userId) {
		LocalDate today = LocalDate.now(clock);
		String className = ClassNames.parse(request.className())
			.orElseThrow(() -> ApiException.validation("className", "must be Nursery, LKG, UKG or 1 to 12"));
		if (request.dob().isAfter(today)) {
			throw ApiException.validation("dob", "cannot be in the future");
		}
		LocalDate joinedOn = (request.joinedOn() != null) ? request.joinedOn() : today;
		List<GuardianRequest> typed = (request.guardians() == null) ? List.of() : request.guardians();
		List<GuardianResponse> siblingPhones = siblingPhones(request.siblingStudentId());
		if (typed.isEmpty() && siblingPhones.isEmpty()) {
			throw ApiException.validation("guardians", "at least one parent phone is needed");
		}

		// The number is taken last among the checks above, so a bad request never touches the counter.
		String admissionNo = numbers.next();
		Student student = students.save(new Student(admissionNo, NameKeys.tidy(request.name()), request.dob(),
				request.gender(), className, tidySection(request.section()), NameKeys.tidy(request.village()),
				tidyOrNull(request.address()), request.fatherOccupation(), joinedOn, userId));

		Set<String> linked = new HashSet<>();
		for (GuardianRequest guardian : typed) {
			GuardianResponse saved = guardianService.linkPhone(student.getId(), guardian.name(), guardian.phone(),
					guardian.relation(), guardian.sms());
			linked.add(saved.phone());
		}
		for (GuardianResponse phone : siblingPhones) {
			if (linked.add(phone.phone())) {
				// A copied phone starts with SMS on. A parent who wants it off for this child says so later.
				guardianService.linkPhone(student.getId(), phone.name(), phone.phone(), phone.relation(), true);
			}
		}

		TransportWarning warning = null;
		if (request.bus() != null) {
			AdmissionBus bus = request.bus();
			LocalDate from = (bus.fromDate() != null) ? bus.fromDate() : joinedOn;
			warning = transportService.start(student.getId(), bus.routeId(), bus.stopId(), from, bus.busFee(), userId)
				.warning();
		}

		auditAdmission(student, request.siblingStudentId(), linked.size(), request.bus() != null);
		return new AdmissionResponse(student.getId(), admissionNo, student.getName(), warning);
	}

	// Rule 4: the phones of the brother or sister.
	private List<GuardianResponse> siblingPhones(Long siblingStudentId) {
		if (siblingStudentId == null) {
			return List.of();
		}
		if (!students.existsById(siblingStudentId)) {
			throw ApiException.validation("siblingStudentId", "does not exist");
		}
		return guardianService.list(siblingStudentId);
	}

	private void auditAdmission(Student student, Long siblingId, int phones, boolean withBus) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("admissionNo", student.getAdmissionNo());
		details.put("phones", phones);
		details.put("bus", withBus);
		if (siblingId != null) {
			details.put("siblingStudentId", siblingId);
		}
		List<String> parts = new ArrayList<>();
		parts.add("Admitted as " + student.getAdmissionNo());
		parts.add("class " + student.getClassName() + ((student.getSection() != null) ? " " + student.getSection() : ""));
		parts.add(student.getVillage());
		auditService.record("STUDENT", student.getId(), AuditAction.CREATED, String.join(", ", parts), details);
	}

	private static String tidySection(String section) {
		String tidy = tidyOrNull(section);
		return (tidy == null) ? null : tidy.toUpperCase(java.util.Locale.ROOT);
	}

	private static String tidyOrNull(String text) {
		String tidy = NameKeys.tidy(text);
		return (tidy == null || tidy.isEmpty()) ? null : tidy;
	}

}

package com.muhjain.school.student;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Locale;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditChanges;
import com.muhjain.school.audit.AuditLog;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.NameKeys;
import com.muhjain.school.common.PageResponse;
import com.muhjain.school.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

	private final UserService userService;

	private final Clock clock;

	public StudentService(StudentRepository students, GuardianService guardianService,
			TransportEnrolmentService transportService, AuditService auditService, UserService userService,
			Clock clock) {
		this.userService = userService;
		this.students = students;
		this.guardianService = guardianService;
		this.transportService = transportService;
		this.auditService = auditService;
		this.clock = clock;
	}

	// The columns the list may be sorted by. Anything else is a 400, so a client cannot sort by a secret column.
	private static final Set<String> SORTABLE = Set.of("name", "admissionNo", "className", "village", "joinedOn");

	private static final int MAX_PAGE_SIZE = 100;

	/**
	 * Rules 22 and 23. Only ACTIVE students unless {@code status = LEFT} is asked.
	 * Example: {@code q = "aryan", bus = YES} → every child whose name has "aryan" and who is on a bus today.
	 *
	 * @param sort like {@code "name,asc"}. Default: name, ascending.
	 * @throws ApiException 400 VALIDATION (page, size, sort, class)
	 */
	@Transactional(readOnly = true)
	public PageResponse<StudentListItem> list(StudentFilter filter, int page, int size, String sort) {
		if (page < 0) {
			throw ApiException.validation("page", "must be 0 or more");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw ApiException.validation("size", "must be between 1 and " + MAX_PAGE_SIZE);
		}
		String className = null;
		if (filter.className() != null && !filter.className().isBlank()) {
			className = ClassNames.parse(filter.className())
				.orElseThrow(() -> ApiException.validation("className", "must be Nursery, LKG, UKG or 1 to 12"));
		}
		StudentFilter checked = new StudentFilter(filter.q(), className, filter.village(), filter.routeId(),
				filter.bus(), filter.status());
		LocalDate today = LocalDate.now(clock);
		Page<Student> found = students.findAll(StudentSpecs.of(checked, today),
				PageRequest.of(page, size, sortOf(sort)));

		List<Long> ids = found.getContent().stream().map(Student::getId).toList();
		Map<Long, String> buses = transportService.busLabels(ids, today);
		Map<Long, String> phones = guardianService.firstPhones(ids);
		return PageResponse.of(found.map(s -> new StudentListItem(s.getId(), s.getName(), s.getAdmissionNo(),
				s.getClassName(), s.getSection(), s.getVillage(), buses.get(s.getId()), phones.get(s.getId()),
				s.getPhotoKey() != null)));
	}

	// "name,desc" → sort by name, descending. The id is the tie-break, so pages never repeat a row.
	private static Sort sortOf(String sort) {
		String property = "name";
		Sort.Direction direction = Sort.Direction.ASC;
		if (sort != null && !sort.isBlank()) {
			String[] parts = sort.split(",");
			property = parts[0].strip();
			if (parts.length > 1) {
				direction = "desc".equalsIgnoreCase(parts[1].strip()) ? Sort.Direction.DESC : Sort.Direction.ASC;
			}
		}
		if (!SORTABLE.contains(property)) {
			throw ApiException.validation("sort", "can be one of " + String.join(", ", SORTABLE.stream().sorted().toList()));
		}
		return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
	}

	/** The full profile. @throws ApiException 404 NOT_FOUND */
	@Transactional(readOnly = true)
	public StudentResponse get(Long id) {
		return toResponse(find(id));
	}

	/**
	 * The change history of one student, newest first, from the audit log.
	 * Example: "Bus started: Route 9, Model Town, from 2 Nov 2026" by Neelam.
	 *
	 * @throws ApiException 404 NOT_FOUND
	 */
	@Transactional(readOnly = true)
	public List<HistoryItem> history(Long id) {
		find(id);
		List<AuditLog> rows = auditService.history(ENTITY, id);
		Map<Long, String> names = userService.displayNames(
				rows.stream().map(AuditLog::getChangedBy).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
		return rows.stream()
			.map(r -> new HistoryItem(r.getSummary(), r.getAction().name(),
					(r.getChangedBy() != null) ? names.get(r.getChangedBy()) : null,
					r.getChangedAt().atZone(clock.getZone()).toOffsetDateTime()))
			.toList();
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
